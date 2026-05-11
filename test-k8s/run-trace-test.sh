#!/usr/bin/env bash
#
# End-to-end trace test for UnifiedMetrics.
#
# 1. Build the trace-verifier distribution and assemble a Docker image.
# 2. Load it into the active minikube cluster.
# 3. Apply the Jaeger + verifier-job manifests.
# 4. Wait for the verifier job to complete and tail its logs.
#
# Usage: ./test-k8s/run-trace-test.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
NS=unifiedmetrics-trace-test
IMAGE=unifiedmetrics-trace-verifier:dev

echo "[1/5] Building trace-verifier..."
( cd "$ROOT" && ./gradlew :trace-verifier:installDist -q )

echo "[2/5] Building Docker image $IMAGE..."
DIST="$ROOT/test-k8s/trace-verifier/build/install/trace-verifier"
DOCKER_CTX="$(mktemp -d)"
trap 'rm -rf "$DOCKER_CTX"' EXIT
mkdir -p "$DOCKER_CTX/app" "$DOCKER_CTX/lib"
# Gradle's installDist puts startup scripts in bin/ and jars in lib/.
cp -r "$DIST/lib/." "$DOCKER_CTX/lib/"
# The verifier's own jar is already in lib/ — no separate app/ needed.
# Re-using the Dockerfile structure that splits app vs. lib for clarity.
cp "$DIST/lib/trace-verifier"*.jar "$DOCKER_CTX/app/" 2>/dev/null || true
cp "$ROOT/test-k8s/trace-verifier/Dockerfile" "$DOCKER_CTX/Dockerfile"

# Build directly inside the minikube docker daemon so we don't have to push.
eval "$(minikube docker-env)"
docker build -t "$IMAGE" "$DOCKER_CTX"

echo "[3/5] Applying manifests..."
kubectl apply -f "$ROOT/test-k8s/tracing-test.yaml"

echo "[4/5] Waiting for jaeger to be ready..."
kubectl -n "$NS" wait --for=condition=available --timeout=180s deploy/jaeger

echo "[4b/5] Re-creating trace-verifier job..."
kubectl -n "$NS" delete job trace-verifier --ignore-not-found
kubectl apply -f "$ROOT/test-k8s/tracing-test.yaml"

echo "[5/5] Waiting for verifier job..."
# Poll the job's conditions for either Complete=True or Failed=True up to ~3 minutes.
DEADLINE=$(( $(date +%s) + 180 ))
RESULT=""
while [[ $(date +%s) -lt $DEADLINE ]]; do
  COMPLETE=$(kubectl -n "$NS" get job trace-verifier -o jsonpath='{.status.conditions[?(@.type=="Complete")].status}' 2>/dev/null || true)
  FAILED=$(kubectl -n "$NS" get job trace-verifier -o jsonpath='{.status.conditions[?(@.type=="Failed")].status}' 2>/dev/null || true)
  if [[ "$COMPLETE" == "True" ]]; then RESULT=COMPLETE; break; fi
  if [[ "$FAILED"   == "True" ]]; then RESULT=FAILED;   break; fi
  sleep 2
done

echo "----- verifier logs -----"
kubectl -n "$NS" logs job/trace-verifier || true
echo "-------------------------"

if [[ "$RESULT" == "COMPLETE" ]]; then
  echo "PASS: trace-verifier job completed successfully"
  exit 0
else
  echo "FAIL: trace-verifier job did not complete (status=${RESULT:-timeout})"
  exit 1
fi
