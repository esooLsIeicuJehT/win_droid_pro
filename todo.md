# Remaining release work

The Windows runtime, container/launch flow, game importing and Mali profiles are implemented. See README.md for scope and WinDroidPro/TEST_PLAN.md for validation.

- Complete physical-phone acceptance on the Moto G 2026 and record per-game results.
- Validate/rebuild upstream prebuilt libraries before claiming 16 KB-page support.
- Use a private signing key for a stable release and retain matching source/notices.
- USB Windows-driver passthrough and the original WSL/service-manager concepts remain outside the implemented runtime path. Do not present them as working features.
