@echo off
setlocal
cd /d "%~dp0"
where flutter >nul 2>nul
if errorlevel 1 (
  echo Flutter not found. Install Flutter 3.35.7 and add its bin folder to PATH.
  pause
  exit /b 1
)
call flutter pub get
if errorlevel 1 goto failed
call flutter gen-l10n
if errorlevel 1 goto failed
call flutter analyze --no-fatal-infos
if errorlevel 1 goto failed
call flutter test
if errorlevel 1 goto failed
call flutter build apk --release
if errorlevel 1 goto failed
echo APK: %CD%\build\app\outputs\flutter-apk\app-release.apk
explorer "build\app\outputs\flutter-apk"
pause
exit /b 0
:failed
echo Build failed. Read the error above.
pause
exit /b 1
