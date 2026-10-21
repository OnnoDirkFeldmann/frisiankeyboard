@echo off
REM ---------------------------------------------------------------------------
REM  Sets up the Interfrisian Keyboard on the connected device.
REM
REM  What it does:
REM    1. grants WRITE_SECURE_SETTINGS so the app can enable itself and set
REM       itself as the default keyboard without any user interaction
REM    2. enables the keyboard
REM    3. makes it the default keyboard
REM
REM  Note: the permission grant is lost when the app is uninstalled, so run this
REM  script again after every "adb install".
REM
REM  Usage:  tools\setup-keyboard.bat
REM ---------------------------------------------------------------------------

setlocal

set "ADB=C:\Users\Neronno\AppData\Local\Android\Sdk\platform-tools\adb.exe"
if not exist "%ADB%" (
    echo [ERROR] adb not found at "%ADB%".
    echo         Add the SDK platform-tools to your PATH or edit this script.
    exit /b 1
)

"%ADB%" get-state >nul 2>&1
if errorlevel 1 (
    echo [ERROR] No device connected.
    echo         Enable USB debugging and plug the device in.
    exit /b 1
)

set "IME=com.frisian.keyboard/.SoftKeyboard"
set "PKG=com.frisian.keyboard"

echo [1/3] Granting WRITE_SECURE_SETTINGS ...
"%ADB%" shell pm grant %PKG% android.permission.WRITE_SECURE_SETTINGS
if errorlevel 1 (
    echo [WARN] The permission could not be granted.
    echo        The app will then guide the user through the setup manually.
)

echo [2/3] Enabling %IME% ...
"%ADB%" shell ime enable %IME%

echo [3/3] Setting %IME% as default ...
"%ADB%" shell ime set %IME%

echo.
echo Current input methods:
"%ADB%" shell ime list -s
echo.
echo Done. The app can now enable and select itself automatically.
endlocal
