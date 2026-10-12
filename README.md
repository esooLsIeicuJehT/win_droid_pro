# WinDroid Pro

WinDroid Pro runs supported Windows programs on ARM64 Android using an embedded, pinned Winlator engine (Wine 10.10, Box64 0.4.4, X server, audio and input). The Android interface provides runtime setup, Windows containers and importing complete game folders.

This is a device-testing build. Building successfully does not establish game compatibility or FPS. Test the Windows desktop first, then a lightweight game before demanding titles.

## What is implemented

- Bundled runtime installation with progress, storage checks and retry after interrupted setup.
- Actual Wine container creation, launching and deletion; existing scaffold database rows are migrated without dropping data.
- Android document-picker imports of extracted folders and files, with executables launched from the library. Imported folders are shared on D: across containers.
- Embedded display, audio, touch/controller layouts and advanced engine settings.
- An 800×600 Vortek/Gladio profile for Mali devices, plus a 640×480 WineD3D/VirGL compatibility profile.
- Android USB device listing. Windows USB-driver passthrough is not implemented.

## Your Moto G

The starting target is a Moto G 2026 with 4 GB physical RAM and Mali graphics. The additional “12 GB” RAM Boost option uses storage; it does not upgrade the GPU or provide 12 GB physical RAM. Start with the Mali profile and test older Windows games. This app does not emulate Switch, 3DS or PS4.

Root is not required. No game performance or demanding Monster Hunter compatibility is promised. See the [phone validation checklist](WinDroidPro/TEST_PLAN.md).

## Build and use

Clone with the pinned runtime submodule:

```sh
git clone --recurse-submodules https://github.com/esooLsIeicuJehT/win_droid_pro.git
cd win_droid_pro/WinDroidPro
python3 -m pip install -r scripts/requirements.txt
./gradlew :app:assembleDebug :app:testDebugUnitTest
python3 scripts/verify_runtime.py app/build/outputs/apk/debug/app-debug.apk
```

Requires JDK 17, Python 3.10+, Android SDK 35, NDK 27.2.12479018 and CMake 3.22.1. Detailed steps are in [BUILD_INSTRUCTIONS.md](WinDroidPro/BUILD_INSTRUCTIONS.md). GitHub Actions builds an APK for pull requests and manual runs.

Install the APK, choose **Install runtime**, create a container, then open its Windows desktop. Use **Game library → Import folder** for a complete extracted game, and select its executable and container. See the [user guide](WinDroidPro/USER_GUIDE.md).

WinDroid Pro uses application ID `com.windroidpro` and can coexist with Winlator. This build targets Android API 28 because its guest programs execute from app-private storage; it is distributed by sideloading, not through Google Play.

## Source and licenses

The existing WinDroid Pro interface was described as MIT-licensed. Embedded engine and dependencies keep their respective licenses; this application is not wholly MIT. See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). All runtime integration changes and rebuild instructions are available in this repository; upstream is pinned at `3981d86efa4f333b2a34a7da8b6521476cd8c8b9`.
