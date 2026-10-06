# WinDroid Pro

WinDroid Pro is an Android project for running Windows applications through a Wine + ARM translation runtime, with container management, graphics translation, and USB host integration as the target architecture.

> **Status: pre-production.** The Android/native foundation builds toward that goal, but the repository does not yet contain a complete Windows runtime. Release builds are intentionally blocked while the required runtime assets are placeholders and until the real provisioning/launch path is implemented. See [`PRODUCTION_READINESS.md`](PRODUCTION_READINESS.md).

## What is implemented

- Android application shell using Kotlin, Compose, Hilt, Room, and JNI/C++.
- Container records and app-private prefix directories.
- USB host UI/native I/O foundation with JNI transfer validation.
- Runtime configuration helpers for registry, networking, services, DXVK/VKD3D setup, and clipboard integration.
- Hardened tar.xz extraction for future runtime payload installation.
- CI gates for unit tests, Android lint, native compilation, and debug APK assembly.
- Release signing configuration sourced outside the repository.
- Release-time integrity gates that reject missing/placeholder runtime archives.

## What is not release-ready yet

The following six files are currently development placeholders and must be replaced by audited, compatible runtime artifacts before release:

- `wine-9.0-x86.tar.xz`
- `wine-9.0-x86_64.tar.xz`
- `box64.tar.xz`
- `mesa.tar.xz`
- `dxvk.tar.xz`
- `vkd3d.tar.xz`

The end-to-end runtime installer, Wine prefix provisioning, Box64/Wine launch chain, graphics backend qualification, application picker/launcher, and physical-device compatibility pass also remain release blockers. WinDroid Pro should not be distributed as a finished emulator until those gates are complete.

## Requirements

### Build

- JDK 17
- Android SDK 34
- Android Build Tools 34.0.0
- Android NDK `26.1.10909125`
- CMake 3.22.1
- Gradle 8.2.1 when a Gradle wrapper is not present

### Target device

The current Android configuration targets API 26+ and builds ARM ABIs. Actual runtime/device support will be documented only after the real runtime stack has been qualified on physical hardware.

## Build a debug APK

```bash
cd WinDroidPro
./build.sh
```

Or, with Gradle 8.2.1 installed:

```bash
cd WinDroidPro
gradle --no-daemon testDebugUnitTest lintDebug assembleDebug
```

The APK is produced under:

```text
WinDroidPro/app/build/outputs/apk/debug/
```

## Release builds

Release builds deliberately fail unless both conditions are true:

1. Every required runtime archive exists, is non-placeholder content, and has a valid XZ header.
2. A new release signing key is provided outside source control.

Expected signing environment variables:

```text
WINDROID_RELEASE_STORE_FILE
WINDROID_RELEASE_STORE_PASSWORD
WINDROID_RELEASE_KEY_ALIAS
WINDROID_RELEASE_KEY_PASSWORD
```

The historical repository keystore must **not** be reused. It was committed to Git history and therefore must be treated as compromised.

## Security and release policy

A successful debug build is not considered proof of emulator functionality. User-visible capabilities must work end-to-end on real hardware before being advertised as supported. Placeholder assets, simulated subsystems, hardcoded release credentials, and fake-success launch paths are release blockers.

See [`PRODUCTION_READINESS.md`](PRODUCTION_READINESS.md) for the full definition of done.

## License

Project source is declared MIT in the existing project metadata. Before distributing bundled Wine, Box64, Mesa, DXVK, VKD3D, or other third-party runtime components, add their exact licenses, source/provenance information, and any required notices to the release package.
