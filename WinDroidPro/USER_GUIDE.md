# Using WinDroid Pro

1. Install the ARM64 APK on Android 8 or later. Android may show an older-target warning because the Wine runtime requires API-28 execution behavior.
2. Keep at least 4 GB free initially, plus storage for your games, and open **Install runtime**. Keep the app open until setup finishes. Setup is bundled and does not require downloading runtime binaries.
3. Open **Manage Containers**, tap **+**, name the container and select **Mali / lower memory** for a Mali phone. Launch its Windows desktop before importing games.
4. Open **Game library → Import folder** and pick the complete, already-extracted game folder. Import supporting DLLs/data alongside the EXE. **Import files** is useful for standalone installers. Zip/7z/RAR extraction is not built in.
5. Tap an executable and choose its container. Imported folders use **D:** in new containers; advanced containers keep their existing mappings and receive a free drive letter when needed. Software installed inside a container uses **C:**. Deleting a container removes its C: files/settings while imported game folders remain shared.
6. Use **Runtime settings** and its drawer → **Containers** to edit graphics, resolution, Wine components and other advanced settings. **Touch and controller layouts** opens the input editor. In a Windows session, Android Back opens the runtime drawer; choose Exit to end the session before launching another game or container.

## Starting on a 4 GB Mali phone

Try 800×600 with Vortek/Gladio and DXVK first. For older games that draw incorrectly, create a separate **Older games / compatibility** container (640×480, WineD3D/VirGL). Compatibility varies by game and phone driver. Vortek uses the phone's Vulkan support; Turnip targets Qualcomm Adreno and is not the Mali profile.

Start with a simple Windows program and older, lightweight games. Lower resolution helps GPU load; it does not fix unsupported APIs, missing Windows components, DRM, anti-cheat or insufficient CPU/RAM. Root and RAM Boost cannot guarantee performance. No PS4, Switch or 3DS emulator is included.

## Troubleshooting

- Setup failure: read the displayed error, free storage and retry. Interrupted setup stages are cleaned safely, and existing runtime/container backups are preserved during replacement.
- Desktop stays on startup: the screen shows the current step and elapsed time. After three minutes, choose **Keep waiting**, **View report**, or **Exit**. The warning does not delete or reset the container. Process failures display an error immediately. **Startup report** on the home screen reopens the saved report; use **Copy** to provide the error text. The report includes device/page-size information, launch stages and recent Wine/Box64 output, stays on the phone and is replaced by the next session.
- Game will not open: test the Windows desktop, confirm the whole game folder was imported, then try the compatibility profile. Some installers require additional Windows components from the advanced container editor.
- Need logs: enable Wine/Box64 logging in Runtime settings, reproduce the failure, and use the session drawer's Logs view. The [test plan](TEST_PLAN.md) lists information needed for useful reports.
- USB: the USB screen inventories Android devices. It does not install Windows drivers or route arbitrary USB tools into Wine.

Back up valuable data before uninstalling or clearing app storage; those actions remove local games and containers. The advanced runtime UI includes container export/import functions. Keep your original game files as well.

**WinDroid Pro Check** is a separate diagnostic installation. Install its runtime and create a Mali container inside it, then launch the desktop and use **Startup report → Copy**. It leaves the normal WinDroid Pro install intact and needs its own free storage. Each CI test APK may have a different debug signer; this diagnostic app avoids replacing your normal installation.
