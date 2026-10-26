@echo off
echo ========================================================
echo  Launching FinTrack JavaFX Application...
echo ========================================================
cd /d "%~dp0"
mvn javafx:run
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [ERROR] Application failed to launch.
    pause
)
