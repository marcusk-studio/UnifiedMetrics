# Changelog

## [0.4.1](https://github.com/marcusk-studio/UnifiedMetrics/compare/v0.4.0...v0.4.1) (2026-05-11)


### Bug Fixes

* **ci:** remove package-name from release-please config ([acf092b](https://github.com/marcusk-studio/UnifiedMetrics/commit/acf092be7100cc2e7d0f8ff84d57029c52fe36af))
* **ci:** remove package-name from release-please config ([81f0fc8](https://github.com/marcusk-studio/UnifiedMetrics/commit/81f0fc85cadb7df1d990f6e1075658c45f81a3f6))

## [0.4.0](https://github.com/marcusk-studio/UnifiedMetrics/compare/v0.3.10...v0.4.0) (2026-05-11)


### Features

* add distributed tracing with OpenTelemetry driver ([3f11319](https://github.com/marcusk-studio/UnifiedMetrics/commit/3f113196e85c6340ee0d00bba2a01f086ab36d85))
* add DogStatsD metrics driver with distribution support ([06973cb](https://github.com/marcusk-studio/UnifiedMetrics/commit/06973cb356d2bcafd7e687295bfa903f926637b2))
* added build guide ([d7be70e](https://github.com/marcusk-studio/UnifiedMetrics/commit/d7be70e1423954d9ece7328ef9030f9461865c77))
* added GC metrics collector ([775e77c](https://github.com/marcusk-studio/UnifiedMetrics/commit/775e77c0297fe03e166042372b0da250c5eb8579))
* added minecraft_events_login_total metric ([e742a6a](https://github.com/marcusk-studio/UnifiedMetrics/commit/e742a6a09a8dd16ab2499583a14b738a64625fd5))
* added prometheus host in config ([a04a87b](https://github.com/marcusk-studio/UnifiedMetrics/commit/a04a87bb271b1b22136890ddaa5de08109feb3b7))
* added support for BungeeCord ([1d8b331](https://github.com/marcusk-studio/UnifiedMetrics/commit/1d8b3312ab475badb0c975e35aaec36d374a0cab))
* added support for BungeeCord ([#27](https://github.com/marcusk-studio/UnifiedMetrics/issues/27)) ([776a5a1](https://github.com/marcusk-studio/UnifiedMetrics/commit/776a5a1b0416c7e9ecab3ab7ecf0198c63ce75cd))
* added support for Prometheus PushGateway ([e21e893](https://github.com/marcusk-studio/UnifiedMetrics/commit/e21e893a313710c4adade3c711825fb7ef77b4ce))
* added the ability to set server name via environment variable ([907c1b5](https://github.com/marcusk-studio/UnifiedMetrics/commit/907c1b5940fbf8443b2754483d28a46566919c76))
* constants for metric names ([ce39520](https://github.com/marcusk-studio/UnifiedMetrics/commit/ce39520febcce8b75085d7bb22d3162efbc11487))
* **fabric:** add fabric support ([#33](https://github.com/marcusk-studio/UnifiedMetrics/issues/33)) ([91094a5](https://github.com/marcusk-studio/UnifiedMetrics/commit/91094a5924c09be3f66b5968e97242266f004c25))
* improved accuracy of GC bytes freed histogram ([e17244e](https://github.com/marcusk-studio/UnifiedMetrics/commit/e17244ea0343047ba2fcdc816923b1412d1f32bd))
* improved default histogram resolution ([8902c52](https://github.com/marcusk-studio/UnifiedMetrics/commit/8902c528a0fb3cf3b73e1a50782ed41cbd0c7840))
* include BungeeCord in CI release ([625e2a4](https://github.com/marcusk-studio/UnifiedMetrics/commit/625e2a4f2a817f286464e49e664fad5236f89550))
* included Minestom in CI ([740b3e2](https://github.com/marcusk-studio/UnifiedMetrics/commit/740b3e26d3beaabe55b10ba97a59807f75f35d81))
* made metrics configurable ([6416d41](https://github.com/marcusk-studio/UnifiedMetrics/commit/6416d410ac62bdc2386411ae0f767acff819825b))
* make use of Kotlin coroutines ([4544e1a](https://github.com/marcusk-studio/UnifiedMetrics/commit/4544e1a10d3cdb1c29ec2f227c3ac0bc3cdb5268))
* minor performance optimization ([33f1115](https://github.com/marcusk-studio/UnifiedMetrics/commit/33f1115a78bcd2dd16fa93dcd9a21b0555482ba2))
* **prometheus:** added authentication for HTTP mode ([c4fc3a7](https://github.com/marcusk-studio/UnifiedMetrics/commit/c4fc3a7a8f8b01d101bc1acd70e163f185b555c2))
* replace konf with kotlinx.serialization ([9355703](https://github.com/marcusk-studio/UnifiedMetrics/commit/93557034e36ffef0f7388f58d8473a4bdad95bf1))
* upgrade dependencies, improved build system ([efe13db](https://github.com/marcusk-studio/UnifiedMetrics/commit/efe13db16ef4c3c0a7eb0db4d14f38a74614929f))


### Bug Fixes

* apiProvider being initialized after saveConfig ([229769b](https://github.com/marcusk-studio/UnifiedMetrics/commit/229769b66c151691c7c689d27f7036e84cbf376c)), closes [#60](https://github.com/marcusk-studio/UnifiedMetrics/issues/60)
* **build/minestom:** use Java 17 as compatibility level ([3b8a77a](https://github.com/marcusk-studio/UnifiedMetrics/commit/3b8a77afa9e6994533e3cf443e866d144c6647b2))
* **build:** dependency issues ([2e61f08](https://github.com/marcusk-studio/UnifiedMetrics/commit/2e61f08ceba2907e65bc5f6d288aa331926ff0bb))
* **build:** downgrade to JDK 16 ([877a465](https://github.com/marcusk-studio/UnifiedMetrics/commit/877a465566ae169fa24cc6a94cf53d7976f03784))
* **build:** escape keyring password ([d7868a7](https://github.com/marcusk-studio/UnifiedMetrics/commit/d7868a70cecd7dcddba3fe1b56e018149dce19f5))
* **build:** fabric compileJava ([697c331](https://github.com/marcusk-studio/UnifiedMetrics/commit/697c331a237250a6002c4c1afbdf9891f08897f8))
* **build:** fix build after dependency upgrades ([9820182](https://github.com/marcusk-studio/UnifiedMetrics/commit/98201825cfacb9f10c41187d76ee8ee6862c610b))
* **build:** gpg key decoding ([cd08e47](https://github.com/marcusk-studio/UnifiedMetrics/commit/cd08e4726de8894d3b5967a7b7f72013811b192a))
* **build:** inject maven secrets ([98f7176](https://github.com/marcusk-studio/UnifiedMetrics/commit/98f7176b8be31dd810ead5f51f0f62a3917508c5))
* **build:** java target version ([5fe085e](https://github.com/marcusk-studio/UnifiedMetrics/commit/5fe085eb84e2360018fbb489b384756c6ba98e13))
* **build:** kapt workaround for JDK 17 ([4d0be44](https://github.com/marcusk-studio/UnifiedMetrics/commit/4d0be44ac94ad86178a093de91a09e5f7c7cfeab))
* **build:** kapt workaround for JDK 17 ([1c28729](https://github.com/marcusk-studio/UnifiedMetrics/commit/1c28729583f06af0aa53c38a6653120d83be0d8a))
* **build:** kapt workaround for JDK 17 ([8ea26a2](https://github.com/marcusk-studio/UnifiedMetrics/commit/8ea26a2a1a388ae848d69dc46099b263568e0a84))
* **build:** publish repository url ([80495c7](https://github.com/marcusk-studio/UnifiedMetrics/commit/80495c7096d01de2f180e7abf8e24bf8f9cfb80f))
* **build:** remove blossom ([9afe0ab](https://github.com/marcusk-studio/UnifiedMetrics/commit/9afe0abc13d832af61be3d4028c0052b89725ffe))
* **build:** remove minestom from build ([71f4c28](https://github.com/marcusk-studio/UnifiedMetrics/commit/71f4c28d5061b4aa8b33c0b3460e12884a6eb512))
* **build:** signArchives and shadowJar ([e0df95a](https://github.com/marcusk-studio/UnifiedMetrics/commit/e0df95acf3fa4ccb2a1a0c10b493688b7e1e855c))
* **build:** signArchives and shadowJar ([8181233](https://github.com/marcusk-studio/UnifiedMetrics/commit/81812332fd28c9eead76ce57460eed9ebce1b2e5))
* **build:** signing parameters ([84a3f7e](https://github.com/marcusk-studio/UnifiedMetrics/commit/84a3f7e2602b270d4d6b9bc187807f61b8eaea29))
* **build:** signing parameters ([fad895d](https://github.com/marcusk-studio/UnifiedMetrics/commit/fad895ddbb8d3de3ab4e9d5289a70c580597bf07))
* **build:** upgrade minestom dependencies ([2a15628](https://github.com/marcusk-studio/UnifiedMetrics/commit/2a15628ec94b03b062c5d742969e3475ef188a48))
* ci configuration to match branch naming ([47e66b9](https://github.com/marcusk-studio/UnifiedMetrics/commit/47e66b967018043f47bb8560a5168af6014bd526))
* compatibility with Java &lt; 16 on Bukkit ([447f1da](https://github.com/marcusk-studio/UnifiedMetrics/commit/447f1da4a2548a053ae9a84f0614a259c6716d17))
* **deps:** update all non-major dependencies ([#113](https://github.com/marcusk-studio/UnifiedMetrics/issues/113)) ([b6b91fb](https://github.com/marcusk-studio/UnifiedMetrics/commit/b6b91fb967d76f750562f2f4cb8341d6088ea45d))
* **deps:** update all non-major dependencies ([#121](https://github.com/marcusk-studio/UnifiedMetrics/issues/121)) ([cb0073e](https://github.com/marcusk-studio/UnifiedMetrics/commit/cb0073e8c074339c4872321653a43874fd2e63ca))
* **deps:** update all non-major dependencies ([#129](https://github.com/marcusk-studio/UnifiedMetrics/issues/129)) ([4598f61](https://github.com/marcusk-studio/UnifiedMetrics/commit/4598f61c904a4c1ecd3c75c84f34566fd47f4ea0))
* **deps:** update all non-major dependencies ([#75](https://github.com/marcusk-studio/UnifiedMetrics/issues/75)) ([7e9c6da](https://github.com/marcusk-studio/UnifiedMetrics/commit/7e9c6da0e2703858ca627dc19c0714d601d7a2b2))
* **deps:** update all non-major dependencies ([#76](https://github.com/marcusk-studio/UnifiedMetrics/issues/76)) ([aee3750](https://github.com/marcusk-studio/UnifiedMetrics/commit/aee375072736d73cb1c619f79f740b40bc3099ab))
* **deps:** update all non-major dependencies ([#79](https://github.com/marcusk-studio/UnifiedMetrics/issues/79)) ([c87b957](https://github.com/marcusk-studio/UnifiedMetrics/commit/c87b9578f33dbf9ad06e78a7c0ee726a539974ef))
* **deps:** update all non-major dependencies ([#82](https://github.com/marcusk-studio/UnifiedMetrics/issues/82)) ([b755682](https://github.com/marcusk-studio/UnifiedMetrics/commit/b755682db5a8eda0a9ee94ee05fc0a80176800ba))
* **deps:** update all non-major dependencies ([#92](https://github.com/marcusk-studio/UnifiedMetrics/issues/92)) ([8edd97a](https://github.com/marcusk-studio/UnifiedMetrics/commit/8edd97a0a81cad2e8064bb03fba33b6008e2376a))
* **deps:** update all non-major dependencies ([#94](https://github.com/marcusk-studio/UnifiedMetrics/issues/94)) ([81e84fe](https://github.com/marcusk-studio/UnifiedMetrics/commit/81e84fe22a636afad9d711c596bac18087005d1e))
* **deps:** update all non-major dependencies ([#99](https://github.com/marcusk-studio/UnifiedMetrics/issues/99)) ([48e154c](https://github.com/marcusk-studio/UnifiedMetrics/commit/48e154ceabf771ac2493f3154e0a5c5ceba19b15))
* **deps:** update dependency com.influxdb:influxdb-client-java to v6.9.0 ([#97](https://github.com/marcusk-studio/UnifiedMetrics/issues/97)) ([9bf8e6c](https://github.com/marcusk-studio/UnifiedMetrics/commit/9bf8e6c9e52a96107bdba4bcf10cb07bc214be95))
* **deps:** update dependency com.influxdb:influxdb-client-java to v7 ([#116](https://github.com/marcusk-studio/UnifiedMetrics/issues/116)) ([a73ebe4](https://github.com/marcusk-studio/UnifiedMetrics/commit/a73ebe459b1ba99b752c8b0062eaadce6c1380c8))
* **docs:** syntax highlighting ([3c4dfd1](https://github.com/marcusk-studio/UnifiedMetrics/commit/3c4dfd1e1fce3db08072e69f3e599044107d14bb))
* **docs:** typo error ([c745b54](https://github.com/marcusk-studio/UnifiedMetrics/commit/c745b54e61621ed22577a71128aa5553cbc6030c))
* **fabric:** fix incorrect loaded chunk calculation ([dbee452](https://github.com/marcusk-studio/UnifiedMetrics/commit/dbee452d93ceae8a35c9abf6cee7af6ff1f5254a)), closes [#42](https://github.com/marcusk-studio/UnifiedMetrics/issues/42)
* **fabric:** fix incorrect mspt calculations with fabric-carpet ([e833e90](https://github.com/marcusk-studio/UnifiedMetrics/commit/e833e90e2da5e80eafb30359ca1c3d1fb9e0dd83)), closes [#41](https://github.com/marcusk-studio/UnifiedMetrics/issues/41)
* **fabric:** tps/mspt on 1.18-pre1 ([#46](https://github.com/marcusk-studio/UnifiedMetrics/issues/46)) ([70c288d](https://github.com/marcusk-studio/UnifiedMetrics/commit/70c288d08e949e6d39412701205fb984ac44a053))
* **fabric:** typo in dependency ([4079c32](https://github.com/marcusk-studio/UnifiedMetrics/commit/4079c322e73d958969de0ba7b9207a452b399c43))
* **fabric:** various issues ([69f39ad](https://github.com/marcusk-studio/UnifiedMetrics/commit/69f39ad9c6899df27b8114b74fb33beec22c12e6))
* gitignore and .idea directory ([5143118](https://github.com/marcusk-studio/UnifiedMetrics/commit/5143118ad48e5d50536899bb3e9eeb92280a1421))
* InfluxDB write interval accuracy ([0909561](https://github.com/marcusk-studio/UnifiedMetrics/commit/090956168e6a506fb7cfd9dde4854493bb8fa15a))
* javadoc in UnifiedMetrics.kt ([97cf85d](https://github.com/marcusk-studio/UnifiedMetrics/commit/97cf85d38420610ade6c5702028cc560719c5d39))
* **minestom:** build against new version ([5f6da83](https://github.com/marcusk-studio/UnifiedMetrics/commit/5f6da83406b400de642a339f1a9795e5fb8e7ce6))
* preview CI conflicting tag ([a696094](https://github.com/marcusk-studio/UnifiedMetrics/commit/a6960942e089e05017c11f8561f95d25de3b1c0b))
* prometheus collector ([fae1c42](https://github.com/marcusk-studio/UnifiedMetrics/commit/fae1c42e66a356d483ee65fe941e34de177606c4))
* remove duplicate BukkitDispatcher class ([6eed577](https://github.com/marcusk-studio/UnifiedMetrics/commit/6eed5773047bfbfbb0b9f4a8a02eb6a27f5852c0))
* typo in README ([d9bbe3c](https://github.com/marcusk-studio/UnifiedMetrics/commit/d9bbe3c64179420f1db66e88d6ac5e000f992df9))
* velocity proxy ping count negative values ([74767f5](https://github.com/marcusk-studio/UnifiedMetrics/commit/74767f59e7646566ed29171fac8225a0272ab5df))
* **velocity:** login counter metric ([cabaf22](https://github.com/marcusk-studio/UnifiedMetrics/commit/cabaf22d508379f54701a00671bf8f5180f7b654)), closes [#87](https://github.com/marcusk-studio/UnifiedMetrics/issues/87)


### Reverts

* downgrade gradle version ([f23ebe9](https://github.com/marcusk-studio/UnifiedMetrics/commit/f23ebe96c6a74076896287bb3cfe317656931bee))
