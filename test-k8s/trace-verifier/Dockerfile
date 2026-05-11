FROM eclipse-temurin:17-jre

WORKDIR /app
COPY app /app/app
COPY lib /app/lib

ENTRYPOINT ["java", "-cp", "/app/app/*:/app/lib/*", "dev.cubxity.plugins.metrics.tracing.test.TraceVerifierKt"]
