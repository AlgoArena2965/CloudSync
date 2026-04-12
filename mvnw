#!/bin/sh
# Maven Wrapper script - downloads maven if needed and runs it
set -e

APP_NAME="Maven"
APP_BASE_NAME=$(basename "$0")

# Add default JVM options
DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m"'

MAVEN_PROJECTBASEDIR="${MAVEN_PROJECTBASEDIR:-$(dirname "$0")}"
MAVEN_BASEDIR="$MAVEN_PROJECTBASEDIR"
MAVEN_OPTS="$MAVEN_OPTS -Dmaven.multiModuleProjectDirectory=$MAVEN_PROJECTBASEDIR"

# Determine the Java command
if [ -n "$JAVA_HOME" ]; then
    JAVACMD="$JAVA_HOME/bin/java"
else
    JAVACMD="java"
fi

# Download Maven if not found
download_maven() {
    MAVEN_VERSION="3.9.9"
    DISTRIBUTION_URL="https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/${MAVEN_VERSION}/apache-maven-${MAVEN_VERSION}-bin.zip"
    MAVEN_DIST_DIR="$MAVEN_PROJECTBASEDIR/.mvn/wrapper/dist"

    mkdir -p "$MAVEN_DIST_DIR"

    if [ ! -f "$MAVEN_DIST_DIR/apache-maven-${MAVEN_VERSION}/bin/mvn" ]; then
        echo "Downloading Maven ${MAVEN_VERSION}..."
        MAVEN_DIST_FILE="$MAVEN_DIST_DIR/apache-maven-${MAVEN_VERSION}-bin.zip"
        curl -fsSL "$DISTRIBUTION_URL" -o "$MAVEN_DIST_FILE"
        unzip -q "$MAVEN_DIST_FILE" -d "$MAVEN_DIST_DIR"
        rm -f "$MAVEN_DIST_FILE"
        echo "Maven downloaded."
    fi

    MAVEN_HOME="$MAVEN_DIST_DIR/apache-maven-${MAVEN_VERSION}"
    export MAVEN_HOME
    MAVEN_BIN="$MAVEN_HOME/bin/mvn"
}

# Check if mvn is available
if ! command -v mvn &> /dev/null; then
    download_maven
    exec "$MAVEN_BIN" "$@"
else
    exec mvn "$@"
fi
