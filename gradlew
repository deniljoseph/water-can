#!/bin/sh
#
# Copyright © 2015-2021 the original authors.
# Licensed under the Apache License, Version 2.0
#

set -e

DIRNAME="$(dirname "$0")"
APP_HOME="$(cd "$DIRNAME" && pwd)"

APP_NAME="Gradle"
APP_BASE_NAME="$(basename "$0")"

CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

# Determine the Java command to use to start the JVM.
if [ -n "$JAVA_HOME" ]; then
    JAVACMD="$JAVA_HOME/bin/java"
    if [ ! -x "$JAVACMD" ]; then
        echo "ERROR: JAVA_HOME is set to an invalid directory: $JAVA_HOME" >&2
        exit 1
    fi
else
    JAVACMD="java"
    if ! command -v java > /dev/null 2>&1; then
        echo "ERROR: JAVA_HOME is not set and no 'java' command found in PATH." >&2
        exit 1
    fi
fi

exec "$JAVACMD" \
    -classpath "$CLASSPATH" \
    org.gradle.wrapper.GradleWrapperMain \
    "$@"
