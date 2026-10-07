# Development

## Requirements

- JDK 17
- Android SDK for compile SDK 36
- Android platform tools for device installation

## Common checks

```sh
./gradlew :app:testStableDebugUnitTest
./gradlew :app:testNightlyDebugUnitTest
./gradlew :app:assemblePlayStableDebug :app:assemblePlayNightlyDebug
```

Build and install a Nightly debug APK with the project wrapper:

```sh
./scripts/build-nightly-debug.sh 26.09 --install
```

The Kotlin namespace is `rocks.pkb.plektra`. The four app identities and their update behaviour are defined in `app/build.gradle.kts`.
