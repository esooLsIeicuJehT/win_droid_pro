package com.windroidpro.runtime

enum class RuntimeProfile(val label: String, val drivers: String, val wrapper: String,
    val width: Int, val height: Int, val driverConfig: String) {
    MALI("Mali / lower memory", "vortek,gladio", "dxvk", 800, 600,
        "maxDeviceMemory=1024,imageCacheSize=64|"),
    COMPATIBILITY("Older games / compatibility", "vortek,virgl", "wined3d", 640, 480,
        "maxDeviceMemory=1024,imageCacheSize=64|"),
    AUTOMATIC("Standard", "vortek,gladio", "dxvk", 1280, 720, "");
}
