@echo off
setlocal EnableExtensions EnableDelayedExpansion

echo ==========================================
echo   WinDroid Pro - Build Script

echo ==========================================

if not exist "settings.gradle" (
    echo [ERROR] Run this script from the WinDroidPro directory.
    exit /b 1
)

where java >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Java is not installed. Install JDK 17.
    exit /b 1
)

echo [OK] Java found

if "%ANDROID_HOME%"=="" if "%ANDROID_SDK_ROOT%"=="" (
    echo [ERROR] Android SDK not found. Set ANDROID_HOME or ANDROID_SDK_ROOT.
    exit /b 1
)
echo [OK] Android SDK found

if exist "gradlew.bat" (
    set "GRADLE_CMD=call gradlew.bat"
) else (
    where gradle >nul 2>&1
    if errorlevel 1 (
        echo [ERROR] No Gradle wrapper is present and gradle is not installed.
        echo         Install Gradle 8.2.1 or generate/commit the wrapper.
        exit /b 1
    )
    set "GRADLE_CMD=gradle"
    echo [INFO] Gradle wrapper is not committed; using installed Gradle.
)

echo.
echo Select build type:
echo 1^) Debug
echo 2^) Release ^(requires real runtime assets and external signing credentials^)
set /p "BUILD_CHOICE=Enter choice [1-2]: "

if "%BUILD_CHOICE%"=="1" (
    set "BUILD_TYPE=Debug"
    set "GRADLE_TASK=assembleDebug"
    set "APK_DIR=app\build\outputs\apk\debug"
) else if "%BUILD_CHOICE%"=="2" (
    set "BUILD_TYPE=Release"
    set "GRADLE_TASK=assembleRelease"
    set "APK_DIR=app\build\outputs\apk\release"
) else (
    echo [ERROR] Invalid choice.
    exit /b 1
)

if "%BUILD_TYPE%"=="Release" (
    set "MISSING_SIGNING="
    if "%WINDROID_RELEASE_STORE_FILE%"=="" set "MISSING_SIGNING=!MISSING_SIGNING! WINDROID_RELEASE_STORE_FILE"
    if "%WINDROID_RELEASE_STORE_PASSWORD%"=="" set "MISSING_SIGNING=!MISSING_SIGNING! WINDROID_RELEASE_STORE_PASSWORD"
    if "%WINDROID_RELEASE_KEY_ALIAS%"=="" set "MISSING_SIGNING=!MISSING_SIGNING! WINDROID_RELEASE_KEY_ALIAS"
    if "%WINDROID_RELEASE_KEY_PASSWORD%"=="" set "MISSING_SIGNING=!MISSING_SIGNING! WINDROID_RELEASE_KEY_PASSWORD"
    if not "!MISSING_SIGNING!"=="" (
        echo [ERROR] Release signing is not configured. Missing:!MISSING_SIGNING!
        exit /b 1
    )
)

echo [INFO] Cleaning previous outputs...
%GRADLE_CMD% --no-daemon clean
if errorlevel 1 exit /b 1

echo [INFO] Building %BUILD_TYPE%...
%GRADLE_CMD% --no-daemon --stacktrace %GRADLE_TASK%
if errorlevel 1 (
    echo [ERROR] Build failed.
    exit /b 1
)

set "APK_PATH="
for %%F in ("%APK_DIR%\*.apk") do (
    if exist "%%~fF" (
        set "APK_PATH=%%~fF"
        echo [OK] APK: %%~fF
    )
)

if "!APK_PATH!"=="" (
    echo [ERROR] Build completed without producing an APK in %APK_DIR%.
    exit /b 1
)

if "%BUILD_TYPE%"=="Debug" (
    where adb >nul 2>&1
    if not errorlevel 1 (
        set /p "INSTALL_CHOICE=Install the debug APK on a connected device? (y/N): "
        if /I "!INSTALL_CHOICE!"=="y" (
            adb install -r "!APK_PATH!"
            if errorlevel 1 (
                echo [ERROR] APK installation failed.
                exit /b 1
            )
            echo [OK] APK installed.
        )
    )
)

echo [OK] %BUILD_TYPE% build completed.
endlocal
exit /b 0
