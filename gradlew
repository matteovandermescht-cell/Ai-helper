#!/usr/bin/env sh
# Wrapper bootstrap: the standard gradle-wrapper.jar is generated in CI.
set -eu
if [ -f "$(dirname "$0")/gradle/wrapper/gradle-wrapper.jar" ]; then
  exec java -classpath "$(dirname "$0")/gradle/wrapper/gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain "$@"
fi
echo "Gradle wrapper JAR missing. Install Gradle 8.9 and run: gradle wrapper --gradle-version 8.9" >&2
exit 1
