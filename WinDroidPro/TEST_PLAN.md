# Validation

## Automated checks

- `python3 -m unittest discover -s scripts -p 'test_*.py' -v`: same-length ELF interpreter relocation and archive content/mode/symlink preservation.
- `./gradlew :app:testDebugUnitTest`: Windows filename validation, repeated imports, path confinement, symlink-safe cleanup and executable/link preservation during copying.
- `./gradlew :app:assembleDebug :app:lintDebug`: Kotlin/Java, native build, resource/manifest packaging and Android lint.
- `python3 scripts/verify_runtime.py app/build/outputs/apk/debug/app-debug.apk`: actual APK assets, guest ELF loaders, path relocation, licenses, ARM64 native libraries and absence of empty runtime placeholders.

## Phone acceptance checklist

These checks require physical ARM64 Android hardware and are not established by a successful build.

- Record exact phone model, Android version, physical RAM, GPU/Vulkan driver, kernel page size, APK version and signer. Root shell `getconf PAGESIZE` or `adb shell getconf PAGESIZE` reports the page size. The bundled prebuilt audio libraries still need validation on 16 KB systems; current device testing should begin on 4 KB-page devices.
- Fresh install: home opens, bundled setup completes, readiness persists after restart, and insufficient free storage produces an actionable message.
- Interrupt setup by force stopping the app; reopen and retry. Confirm previously installed container data is preserved on a runtime update.
- Create the default Mali container. Confirm Windows desktop, mouse/touch, keyboard, audio and clean session exit. Repeat with the older-games profile.
- Import a full folder containing an EXE, DLL and data file. Confirm folder layout on D:, launch from the library and launch an installer into C:. Import the same folder twice; neither copy should overwrite the first.
- Cancel a picker, deny document access, rotate during import and test an invalid/reserved Windows filename. Confirm the UI remains usable and partial imports are hidden/cleaned.
- Delete one container. Its C: files disappear, imported games survive, and other containers still launch. Delete or duplicate a container in advanced settings; the Compose list reflects it on returning.
- Update an older scaffold installation with the same signer. Existing Room rows remain; their Windows environments are created on first launch. The old scaffold did not contain working Wine prefixes, so there are no old runtime binaries to preserve.
- Test touch layouts and a paired controller. Check suspend/resume, device rotation and background behavior.
- Test a lightweight Windows game for 10 minutes before a heavier game. Record title/version, graphics profile, resolution, steady FPS, temperature and whether it crashes. Compare RAM Boost settings using the same scene; the storage-backed setting is not physical RAM.

For failures include the error text or Wine/Box64 log, game build, phone details and reproduction steps. Successful desktop launch does not mean every Windows game is compatible.
