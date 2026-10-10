# Build instructions

For an isolated phone test, add `-PwindroidCheck=true` to the Gradle build command. This builds **WinDroid Pro Check** (`com.windroiddbg`) alongside the normal app and relocates its guest binaries to that app's private directory. It requires its own runtime setup and does not share containers or games with the main install. GitHub Actions publishes both APK variants. Debug signing keys from separate CI runs may differ; avoid uninstalling an existing app with valuable data to work around a signature mismatch.

Use a Linux, macOS or Windows build host with JDK 17, Python 3.10+ and Android SDK command-line tools. Official NDK host tools do not run directly in Android/Termux; use GitHub Actions when building from your phone.

## First build

From the repository root:

```sh
git submodule update --init --recursive
cd WinDroidPro
python3 -m pip install -r scripts/requirements.txt
sdkmanager "platforms;android-35" "build-tools;34.0.0" "platform-tools" "ndk;27.2.12479018" "cmake;3.22.1"
sdkmanager --licenses
```

Set `JAVA_HOME` to your JDK 17 and `ANDROID_HOME` to the SDK directory, or create ignored `local.properties` with `sdk.dir=/absolute/path/to/android-sdk`. On Windows use `gradlew.bat` and set `WINDROID_PYTHON=python` if `python3` is unavailable.

```sh
python3 -m unittest discover -s scripts -p 'test_*.py' -v
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
python3 scripts/verify_runtime.py app/build/outputs/apk/debug/app-debug.apk
```

The debug APK is signed for local testing and appears at `app/build/outputs/apk/debug/app-debug.apk`. First builds download Gradle/dependencies, relocate bundled runtime paths and compile native code; allow several minutes and several GB of build storage. Runtime preparation is cached in `runtime/build/prepared`.

The preparation script requires the pinned, unmodified upstream commit. Change its pin and review all patches together when updating the engine. Never change `com.windroidpro` or the `rfs` runtime-directory name without also reviewing guest ELF paths; their equal-length replacement preserves binary offsets.

## Release

```sh
./gradlew :app:assembleRelease
```

The release APK is **unsigned**. Use your own private signing key and Android SDK `zipalign` followed by `apksigner`; do not use a repository-provided key. Keep the same signer for future upgrades. Runtime JNI classes are retained by consumer rules.

## GitHub Actions

The Build APK workflow checks out submodules, installs Python/runtime tools, runs host and JVM tests plus Android lint, builds the debug APK, checks bundled guest loaders and uploads the APK as an artifact. It runs on pull requests, pushes to main/master and manual dispatch. A passing workflow does not replace testing on an ARM64 Android phone.
