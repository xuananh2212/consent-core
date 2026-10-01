#!/bin/sh
set -eu

if command -v mvn >/dev/null 2>&1; then
  exec mvn "$@"
fi

MAVEN_VERSION=3.9.16
BASE_DIR="${HOME}/.m2/wrapper/dists/apache-maven-${MAVEN_VERSION}"
MAVEN_HOME="${BASE_DIR}/apache-maven-${MAVEN_VERSION}"
ARCHIVE="${BASE_DIR}/apache-maven-${MAVEN_VERSION}-bin.tar.gz"
PRIMARY_URL="https://dlcdn.apache.org/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz"
FALLBACK_URL="https://archive.apache.org/dist/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz"

if [ ! -x "${MAVEN_HOME}/bin/mvn" ]; then
  mkdir -p "${BASE_DIR}"
  if command -v curl >/dev/null 2>&1; then
    curl -fL "${PRIMARY_URL}" -o "${ARCHIVE}" || curl -fL "${FALLBACK_URL}" -o "${ARCHIVE}"
  elif command -v wget >/dev/null 2>&1; then
    wget -O "${ARCHIVE}" "${PRIMARY_URL}" || wget -O "${ARCHIVE}" "${FALLBACK_URL}"
  else
    echo "Maven is not installed and neither curl nor wget is available." >&2
    echo "Use IntelliJ's bundled Maven or install Maven 3.9+." >&2
    exit 1
  fi
  tar -xzf "${ARCHIVE}" -C "${BASE_DIR}"
fi

exec "${MAVEN_HOME}/bin/mvn" "$@"
