# Third-party source and notices

WinDroid Pro embeds [brunodev85/winlator-app](https://github.com/brunodev85/winlator-app) at commit `3981d86efa4f333b2a34a7da8b6521476cd8c8b9`. Its checked-in [LICENSE](WinDroidPro/vendor/winlator/LICENSE) is LGPL 2.1. Keep its notices and copyright statements. The full corresponding engine source is available through the pinned submodule; modifications are reproduced by `WinDroidPro/scripts/prepare_runtime.py`. Build instructions allow rebuilding/replacing this library and re-signing the complete application.

Integration changes include WinDroid Pro private paths, equal-length relocation of guest ELF strings and symlink destinations, a runtime FileProvider, Android-library menu-ID adaptation, a Context-compatible preference reset and an exposed-extension delimiter fix and explicit native standard-library headers required by NDK 27. The original engine is not edited in place. The APK includes `WINLATOR-LICENSE.txt`, native component license files and `windroid-runtime.json` recording the exact source pin and relocation counts.

The upstream distribution includes Wine (LGPL), Box64 (MIT), Mesa/VirGL and Vulkan-related components, DXVK and VKD3D, PulseAudio, FluidSynth, and other libraries with their own licenses. They retain their original upstream notices and source provenance. The runtime source also contains BSD-2-Clause libadrenotools/linkernsbypass and LGPL-2.1 FluidSynth license texts, copied into APK assets. The pinned engine repository is the source of bundled runtime assets; component source projects include:

- [Wine](https://gitlab.winehq.org/wine/wine)
- [Box64](https://github.com/ptitSeb/box64)
- [DXVK](https://github.com/doitsujin/dxvk)
- [VKD3D-Proton](https://github.com/HansKristian-Work/vkd3d-proton)
- [Mesa](https://gitlab.freedesktop.org/mesa/mesa)
- [Vortek](https://github.com/brunodev85/vortek)
- [Gladio](https://github.com/brunodev85/gladio)
- [Winlator runtime distribution and glibc patches](https://github.com/brunodev85/winlator)

AndroidX, Kotlin, Compose, Hilt, Room and Gradle dependencies retain their respective licenses. The original project described its own UI as MIT-licensed; that description does not replace the embedded engine/dependency licenses. Distribute corresponding source and notices alongside binary releases as required by each component's license.
