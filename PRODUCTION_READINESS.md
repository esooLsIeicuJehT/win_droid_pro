# WinDroid Pro production readiness

Status: **PRE-PRODUCTION / RELEASE BLOCKED**

This document is intentionally strict. A debug APK compiling is not the same thing as a production-ready Windows runtime.

## Completed hardening in this pass

- CI runs from the real `WinDroidPro` project directory and uses supported GitHub Actions.
- Unit tests, Android lint, native compilation, and debug APK assembly are CI gates.
- Release signing credentials are no longer hardcoded in source.
- The previously committed release keystore has been removed from the current tree and must be considered compromised because it remains in Git history.
- Release builds fail when any required runtime archive is missing, tiny, or not an XZ archive.
- Runtime archive extraction rejects path traversal, links, unsupported entry types, excessive entry counts, and excessive expanded size.
- USB JNI calls validate Java buffer bounds and transfer parameters before native I/O.
- Native library loading fails closed instead of waiting forever after a load failure.
- Broad legacy storage, package-install, and unused foreground-service permissions were removed.
- The unused broad FileProvider was removed.
- Fake container-launch success messaging was removed.
- Simulated WSL behavior was removed. WSL now reports unsupported until a real backend exists.
- Native Wine initialization no longer reports success unless an executable Wine runtime is actually available.

## Release blockers

A production release MUST NOT be published until all of these are complete:

1. **Real runtime payload**
   - Replace the six zero-byte runtime placeholders with audited, licensed runtime artifacts.
   - Record exact upstream source, version, checksum, license, and build provenance for each artifact.
   - Do not mix arbitrary Wine/Box64/DXVK/Mesa/VKD3D bundles. They must be built and tested as one compatible runtime stack.

2. **Runtime installer and launcher**
   - Install/extract Wine, Box64 and graphics components into app-private storage.
   - Make the runtime binaries executable and construct a deterministic runtime `PATH`/library path.
   - Initialize a Wine prefix using the installed runtime.
   - Launch a selected Windows executable through a supervised process model.
   - Capture stdout/stderr and exit status and surface real launch errors to the UI.
   - Add cancellation/cleanup for launched processes.

3. **Graphics integration**
   - Verify Mesa/Turnip/Zink support by GPU family.
   - Install DXVK/VKD3D into the correct Wine prefix architecture paths.
   - Validate Vulkan/OpenGL capability before enabling a backend.
   - Test graceful fallback when Vulkan is unavailable.

4. **Container lifecycle**
   - Create a complete prefix during container creation, not only an empty directory.
   - Add explicit states such as provisioning, ready, running, failed, and stopped.
   - Persist last launch result and diagnostics.
   - Prevent users from starting a container until provisioning completes successfully.

5. **Application launcher**
   - Implement a file picker/import flow for `.exe`/`.msi` files using scoped-storage APIs.
   - Persist installed/registered Windows applications.
   - Wire the Run action to the real launcher instead of a UI-only action.

6. **Settings**
   - Remove or implement controls that currently only change in-memory Compose state.
   - Persist supported settings through DataStore or Room.
   - Apply every exposed setting to the actual runtime or omit it from release UI.

7. **USB passthrough validation**
   - Use Android `UsbManager` permission/grant flow and owned file descriptors.
   - Test attach, detach, claim/release, control and bulk transfers on physical devices.
   - Add timeouts/cancellation for blocking reads.
   - Build a device compatibility matrix.

8. **Release signing and supply chain**
   - Generate a brand-new production signing key outside the repository.
   - Store signing material only in an approved secret store / CI secret.
   - Produce checksums for release artifacts.
   - Add dependency and runtime provenance documentation.
   - Add third-party license notices for every bundled runtime component.

9. **Release CI**
   - Add a signed release workflow after runtime artifacts and signing secrets exist.
   - Gate release on tests, lint, runtime integrity checks, and signed artifact verification.
   - Never publish an artifact from a failed or bypassed gate.

10. **Device qualification**
    - Smoke-test installation, first launch, container creation, runtime provisioning, application launch and cleanup on supported Android versions.
    - Test at least one Adreno and one non-Adreno device if both are claimed supported.
    - Record crash/ANR results and memory/storage requirements from real devices.

## Current runtime placeholders

The following files are development placeholders and intentionally block release builds until replaced:

- `WinDroidPro/app/src/main/assets/wine-9.0-x86.tar.xz`
- `WinDroidPro/app/src/main/assets/wine-9.0-x86_64.tar.xz`
- `WinDroidPro/app/src/main/assets/box64.tar.xz`
- `WinDroidPro/app/src/main/assets/mesa.tar.xz`
- `WinDroidPro/app/src/main/assets/dxvk.tar.xz`
- `WinDroidPro/app/src/main/assets/vkd3d.tar.xz`

## Definition of done

WinDroid Pro is production-ready only when CI is green, the runtime is real and provenance-verified, a clean install can provision a container and launch a tested Windows application on supported hardware, release signing is external to source control, and every user-visible feature either works end-to-end or is absent from the release UI.
