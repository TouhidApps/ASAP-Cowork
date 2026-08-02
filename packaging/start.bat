@echo off
setlocal
cd /d "%~dp0"

where java >nul 2>nul
if errorlevel 1 (
    echo Java is not installed or not on your PATH. Install a Java 21+ runtime and try again.
    pause
    exit /b 1
)

echo Starting build-runner...
start "ASAP-Cowork build-runner (do not close until you are done)" /min cmd /c "java -jar build-runner-all.jar > build-runner.log 2>&1"

echo Waiting for build-runner to come up...
timeout /t 5 /nobreak >nul

echo Starting chat-gateway...
echo Once ready, ASAP-Cowork will open at http://localhost:8081
start "" http://localhost:8081
java -jar chat-gateway-all.jar

echo.
echo chat-gateway stopped. Close the separate "ASAP-Cowork build-runner" window too when you are done.
pause
