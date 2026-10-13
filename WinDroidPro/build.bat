@echo off
setlocal
cd /d "%~dp0"
if not defined WINDROID_PYTHON set WINDROID_PYTHON=python
set VARIANT=Debug
if /i "%~1"=="release" set VARIANT=Release
if not "%~1"=="" if /i not "%~1"=="debug" if /i not "%~1"=="release" (
  echo Usage: build.bat [debug^|release]
  exit /b 2
)
"%WINDROID_PYTHON%" -m unittest discover -s scripts -p "test_*.py" -v
if errorlevel 1 exit /b 1
call gradlew.bat :app:assemble%VARIANT% :app:testDebugUnitTest :app:lintDebug --console=plain
if errorlevel 1 exit /b 1
set APK=app\build\outputs\apk\debug\app-debug.apk
if "%VARIANT%"=="Release" set APK=app\build\outputs\apk\release\app-release-unsigned.apk
"%WINDROID_PYTHON%" scripts\verify_runtime.py "%APK%"
if errorlevel 1 exit /b 1
echo APK: %APK%
