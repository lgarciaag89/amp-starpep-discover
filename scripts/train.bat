@echo off
setlocal

for %%I in ("%~dp0..") do set "APP_DIR=%%~fI"
set "JAR=%APP_DIR%\target\amp-starpep-discover.jar"

if defined JAVA (
    set "JAVA_CMD=%JAVA%"
) else if defined JAVA_HOME (
    set "JAVA_CMD=%JAVA_HOME%\bin\java.exe"
) else (
    set "JAVA_CMD=java"
)

if defined JAVA_HOME if not defined JAVA if not exist "%JAVA_CMD%" (
    echo Java executable not found: "%JAVA_CMD%" 1>&2
    exit /b 1
)

set "JAVA_VERSION_FILE=%TEMP%\starpep-java-version-%RANDOM%.tmp"
"%JAVA_CMD%" -XshowSettings:properties -version > "%JAVA_VERSION_FILE%" 2>&1
if errorlevel 1 (
    del "%JAVA_VERSION_FILE%" >nul 2>&1
    echo Could not determine the Java version from "%JAVA_CMD%". 1>&2
    exit /b 1
)
for /f "tokens=2 delims== " %%V in ('findstr /C:"java.specification.version" "%JAVA_VERSION_FILE%"') do set "JAVA_VERSION=%%V"
del "%JAVA_VERSION_FILE%" >nul 2>&1
if not defined JAVA_VERSION (
    echo Could not determine the Java version from "%JAVA_CMD%". 1>&2
    exit /b 1
)
if %JAVA_VERSION% LSS 21 (
    echo Java 21 or newer is required; found Java %JAVA_VERSION%. 1>&2
    exit /b 1
)

if not exist "%JAR%" (
    echo Packaged application not found. Run "mvn clean package" in "%APP_DIR%" first. 1>&2
    exit /b 1
)
"%JAVA_CMD%" -cp "%JAR%" tomocomd.BuildModel %*
exit /b %ERRORLEVEL%
