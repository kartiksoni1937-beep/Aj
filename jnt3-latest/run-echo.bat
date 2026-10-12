@echo off
REM Batch script to run JNT with Echo profile configuration
REM This script should be run from the jnt directory

cd /d "%~dp0"
java -cp "libraries\asm.jar;target\jnt-1.0-SNAPSHOT-jar-with-dependencies.jar" war.Entrypoint --config .\profiles\echo\config.yml

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo Error: Java execution failed with error code %ERRORLEVEL%
    pause
    exit /b %ERRORLEVEL%
)

pause
