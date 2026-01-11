@echo off
setlocal EnableDelayedExpansion

REM Add Android SDK's CMake and Ninja to PATH
set "ANDROID_SDK_CMAKE=C:\Users\crt106\AppData\Local\Android\Sdk\cmake\4.1.2\bin"
if exist "%ANDROID_SDK_CMAKE%" (
    set "PATH=%ANDROID_SDK_CMAKE%;%PATH%"
)

cmake --preset android-release -DCMAKE_POLICY_VERSION_MINIMUM=3.5 -DCMAKE_C_COMPILER_LAUNCHER= -DCMAKE_CXX_COMPILER_LAUNCHER=
if %ERRORLEVEL% neq 0 goto :error

cmake --build --preset android-release
if %ERRORLEVEL% neq 0 goto :error

if not exist lib mkdir lib
xcopy /Y /E .\build\android\qnnlibs ..\assets\qnnlibs\
if %ERRORLEVEL% neq 0 goto :error

if not exist ..\jniLibs\arm64-v8a mkdir ..\jniLibs\arm64-v8a
xcopy /Y .\build\android\bin\arm64-v8a\libstable_diffusion_core.so ..\jniLibs\arm64-v8a\
if %ERRORLEVEL% neq 0 goto :error

echo Build completed successfully
goto :eof

:error
echo Failed with error #%ERRORLEVEL%.
exit /b %ERRORLEVEL%
