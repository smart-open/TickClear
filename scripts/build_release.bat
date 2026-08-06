@echo off
setlocal EnableExtensions
rem ============================================================================
rem  build_release.bat - TickClear release build (Windows)
rem  Mirrors scripts/build_release.sh:
rem    1. Generate keystore (RSA 2048 / 10000 days, PKCS12) if missing
rem       - store/key share ONE random password (PKCS12 does not allow separate)
rem    2. Write release.* signing config into local.properties
rem    3. Run assembleRelease, then verify v2/v3 signature via apksigner
rem
rem  Usage:
rem    build_release.bat                    reuse keystore / create on first run
rem    build_release.bat --force-keystore   regenerate keystore (old file moved to .old.<ts>)
rem
rem  Security:
rem    - keystore\ is gitignored; losing it = losing the publishing identity.
rem    - Back up keystore\keystore.properties offline (USB / password manager).
rem  NOTE: This file is pure ASCII on purpose - Chinese text would be garbled
rem        under cmd's default GBK code page.
rem ============================================================================

rem ---- enter project root ----
cd /d "%~dp0.."
set "ROOT=%CD%"

if not defined JAVA_HOME set "JAVA_HOME=D:/software/jvms_v2.1.6/store/jdk-21.0.6"
if not defined ANDROID_HOME set "ANDROID_HOME=C:/Android"

set "KEYSTORE_DIR=%ROOT%\keystore"
set "KEYSTORE=keystore\release.jks"
set "KEYSTORE_ABS=%KEYSTORE_DIR%\release.jks"
set "KEYSTORE_PROPS=%KEYSTORE_DIR%\keystore.properties"
set "LOCAL_PROPS=%ROOT%\local.properties"
set "ALIAS=tickclear"

echo ==^> [1/3] Check / generate release keystore
if exist "%KEYSTORE_ABS%" goto :has_key
goto :gen_key

:has_key
if /i not "%~1"=="--force-keystore" goto :read_props
set "TS=%date:~0,4%%date:~5,2%%date:~8,2%_%time:~0,2%%time:~3,2%%time:~6,2%"
move /y "%KEYSTORE_ABS%" "%KEYSTORE_ABS%.old.%TS%" >nul
echo     old keystore moved to backup: %KEYSTORE_ABS%.old.%TS%
goto :gen_key

:gen_key
if not exist "%KEYSTORE_DIR%" mkdir "%KEYSTORE_DIR%"
rem 20-digit random password + suffix; store/key MUST be identical (PKCS12)
set "STORE_PASS=%RANDOM%%RANDOM%%RANDOM%%RANDOM%tc2026"
set "KEY_PASS=%STORE_PASS%"
"%JAVA_HOME%\bin\keytool.exe" -genkeypair -keystore "%KEYSTORE%" -alias "%ALIAS%" -keyalg RSA -keysize 2048 -validity 10000 -storepass "%STORE_PASS%" -keypass "%KEY_PASS%" -dname "CN=TickClear, OU=Mobile, O=TickClear, L=Shenzhen, ST=Guangdong, C=CN"
if errorlevel 1 (
    echo FAILED: keytool generation failed. Check JAVA_HOME=%JAVA_HOME%
    exit /b 1
)
>  "%KEYSTORE_PROPS%" echo storePassword=%STORE_PASS%
>> "%KEYSTORE_PROPS%" echo keyPassword=%KEY_PASS%
>> "%KEYSTORE_PROPS%" echo keyAlias=%ALIAS%
echo     keystore generated: %KEYSTORE_ABS%
echo     passwords saved to keystore\keystore.properties - BACK IT UP OFFLINE!
goto :read_props

:read_props
if not exist "%KEYSTORE_PROPS%" (
    echo FAILED: missing %KEYSTORE_PROPS% ^(keystore exists but password file lost^)
    exit /b 1
)
for /f "tokens=1,* delims==" %%a in ('findstr /b /c:"storePassword=" "%KEYSTORE_PROPS%"') do set "STORE_PASS=%%b"
for /f "tokens=1,* delims==" %%a in ('findstr /b /c:"keyPassword="   "%KEYSTORE_PROPS%"') do set "KEY_PASS=%%b"
for /f "tokens=1,* delims==" %%a in ('findstr /b /c:"keyAlias="     "%KEYSTORE_PROPS%"') do set "ALIAS=%%b"
if not defined STORE_PASS (
    echo FAILED: cannot parse %KEYSTORE_PROPS%
    exit /b 1
)
echo     using keystore: %KEYSTORE_ABS% ^(alias %ALIAS%^)

echo ==^> [2/3] Write local.properties signing config ^(keep existing non-release.* keys^)
if exist "%LOCAL_PROPS%" (
    findstr /v /b /c:"release." "%LOCAL_PROPS%" > "%LOCAL_PROPS%.tmp"
    move /y "%LOCAL_PROPS%.tmp" "%LOCAL_PROPS%" >nul
)
set "STORE_FILE=%KEYSTORE_ABS%"
set "STORE_FILE=%STORE_FILE:\=/%"
>> "%LOCAL_PROPS%" echo release.storeFile=%STORE_FILE%
>> "%LOCAL_PROPS%" echo release.storePassword=%STORE_PASS%
>> "%LOCAL_PROPS%" echo release.keyAlias=%ALIAS%
>> "%LOCAL_PROPS%" echo release.keyPassword=%KEY_PASS%
echo     local.properties updated

echo ==^> [3/3] Build Release APK
"%JAVA_HOME%\bin\java.exe" -cp gradle\wrapper\gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain --no-daemon assembleRelease
if errorlevel 1 (
    echo BUILD FAILED
    exit /b 1
)

echo.
echo ==========================================
echo Build complete:
dir /b app\build\outputs\apk\release\app-release.apk
echo.
echo --- Signature verify ^(apksigner, v2/v3^) ---
set "APKSIGNER_JAR=%ANDROID_HOME%\build-tools\34.0.0\lib\apksigner.jar"
if exist "%APKSIGNER_JAR%" (
    "%JAVA_HOME%\bin\java.exe" -jar "%APKSIGNER_JAR%" verify --print-certs app\build\outputs\apk\release\app-release.apk
) else (
    echo ^(apksigner.jar not found at %APKSIGNER_JAR% - verify signature manually^)
)
echo ==========================================
endlocal
