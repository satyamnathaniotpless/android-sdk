# Publishing the SDKs to Maven

This repo contains three Android library modules you can publish as Maven artifacts:

- `com.otplesssdk:utils-sdk:<version>`
- `com.otplesssdk:otp-sdk:<version>`
- `com.otplesssdk:sna-sdk:<version>`
- `com.otplesssdk:auth-core-sdk:<version>`

The publishing configuration lives in:
- `android/publish.gradle` (shared publishing logic)
- `android/gradle.properties` (defaults for `GROUP` + `VERSION_NAME` + POM metadata)

## 1) Set version + metadata

Edit `android/gradle.properties`:

- `GROUP` (default: `com.otplesssdk`)
- `VERSION_NAME` (example: `1.0.0`)
- `POM_*` values (URLs/license/developer info)

## 2) Publish locally (recommended first)

Publishes into your local Maven cache (`~/.m2/repository`):

```bash
cd android
./gradlew :utils-sdk:publishToMavenLocal
./gradlew :otp-sdk:publishToMavenLocal
./gradlew :sna-sdk:publishToMavenLocal
./gradlew :auth-core-sdk:publishToMavenLocal
```

Then consume from another project as:

```gradle
repositories { mavenLocal(); mavenCentral() }
dependencies {
  implementation("com.otplesssdk:utils-sdk:<version>")
  implementation("com.otplesssdk:otp-sdk:<version>")
  implementation("com.otplesssdk:sna-sdk:<version>")
  implementation("com.otplesssdk:auth-core-sdk:<version>")
}
```

## 3) Publish to a folder (build-only repo)

Publishes to `android/build/repo` (useful for CI verification):

```bash
cd android
./gradlew :utils-sdk:publishReleasePublicationToLocalBuildRepoRepository
./gradlew :otp-sdk:publishReleasePublicationToLocalBuildRepoRepository
./gradlew :sna-sdk:publishReleasePublicationToLocalBuildRepoRepository
./gradlew :auth-core-sdk:publishReleasePublicationToLocalBuildRepoRepository
```

## 4) Publish to a remote Maven repository (generic)

Provide the repository URL + credentials at publish time:

```bash
cd android
./gradlew :utils-sdk:publish \
  -PmavenUrl="https://your.maven.repo/repository/releases/" \
  -PmavenUsername="..." \
  -PmavenPassword="..."
```

Repeat for `:otp-sdk` and `:sna-sdk`, or run `./gradlew publish` to publish all modules.

You can also pass credentials via environment variables:
- `MAVEN_USERNAME`
- `MAVEN_PASSWORD`

## 5) Signing (usually required for Maven Central)

If your target repo requires signed artifacts, pass:

- `-PsigningKey="-----BEGIN PGP PRIVATE KEY BLOCK-----\n...\n-----END PGP PRIVATE KEY BLOCK-----"`
- `-PsigningPassword="..."`

or set environment variables:
- `SIGNING_KEY`
- `SIGNING_PASSWORD`