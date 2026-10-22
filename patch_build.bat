@echo off
cd /d "%~dp0"
pythonw patcher_gui.py
if %errorlevel% neq 0 (
    echo.
    echo  Failed to open GUI. Make sure Python 3 is installed.
    echo  https://python.org
    echo.
    pause
)
