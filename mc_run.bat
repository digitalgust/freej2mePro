@echo off
setlocal EnableExtensions

rem Optional overrides when Java is not in PATH:
rem set "JAVA_HOME=C:\Program Files\Java\jdk1.8.0_202"
rem set "JAVA=C:\Program Files\Java\jdk1.8.0_202\bin\java.exe"

set "ROOT=%~dp0"
set "FREEJ2ME_JAR=%ROOT%freej2me.jar"
set "TARGET_JAR=%ROOT%mc-c0.0.11a.jar"

if not defined JAVA if defined JAVA_HOME (
    set "JAVA=%JAVA_HOME%\bin\java.exe"
)

if not defined JAVA (
    set "JAVA=java"
)

if not exist "%FREEJ2ME_JAR%" (
    echo [ERROR] FreeJ2ME library not found: "%FREEJ2ME_JAR%"
    exit /b 1
)

if not exist "%TARGET_JAR%" (
    echo [ERROR] Built JAR not found: "%TARGET_JAR%"
    exit /b 1
)

call :check_tool "%JAVA%" java
if errorlevel 1 exit /b 1

echo Launching FreeJ2ME...
"%JAVA%" -cp "%FREEJ2ME_JAR%" org.recompile.freej2me.FreeJ2ME file:///"%TARGET_JAR%"
exit /b %errorlevel%

:check_tool
if exist "%~1" exit /b 0
where "%~1" >nul 2>nul
if errorlevel 1 (
    echo [ERROR] %~2 not found. Set JAVA, set JAVA_HOME, or add the JDK/JRE bin folder to PATH.
    exit /b 1
)
exit /b 0
