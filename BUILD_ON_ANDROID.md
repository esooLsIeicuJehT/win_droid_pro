# Building from your phone

Use GitHub Actions for this project: open the repository → Actions → Build APK → Run workflow, choose the implementation branch, and download the APK artifact after a successful run.

The official Android NDK uses desktop host executables and does not run directly inside ordinary Android/Termux. A custom Termux toolchain would require a separate port and is not supported by these build instructions. See [desktop build instructions](WinDroidPro/BUILD_INSTRUCTIONS.md) and the [phone guide](WinDroidPro/USER_GUIDE.md).
