@echo off
if exist "%~dp0gradle\wrapper\gradle-wrapper.jar" (
  java -classpath "%~dp0gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
) else (
  echo Gradle wrapper JAR missing. Install Gradle 8.9 and run: gradle wrapper --gradle-version 8.9
  exit /b 1
)
