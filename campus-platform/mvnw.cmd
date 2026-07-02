@echo off
setlocal

set MVNW_DIR=%~dp0
set MAVEN_VERSION=3.9.9
set MAVEN_HOME=%MVNW_DIR%.mvn\apache-maven-%MAVEN_VERSION%
set MAVEN_BIN=%MAVEN_HOME%\bin\mvn.cmd
set MAVEN_ZIP=%MVNW_DIR%.mvn\apache-maven-%MAVEN_VERSION%-bin.zip

if not exist "%MAVEN_BIN%" (
  if not exist "%MVNW_DIR%.mvn" mkdir "%MVNW_DIR%.mvn"
  powershell -NoProfile -ExecutionPolicy Bypass -Command ^
    "$ErrorActionPreference='Stop';" ^
    "$url='https://archive.apache.org/dist/maven/maven-3/%MAVEN_VERSION%/binaries/apache-maven-%MAVEN_VERSION%-bin.zip';" ^
    "Invoke-WebRequest -Uri $url -OutFile '%MAVEN_ZIP%';" ^
    "Expand-Archive -LiteralPath '%MAVEN_ZIP%' -DestinationPath '%MVNW_DIR%.mvn' -Force"
)

call "%MAVEN_BIN%" %*
