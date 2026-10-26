#!/bin/sh
set -eu
cd "$(dirname "$0")"
export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
export PATH="$JAVA_HOME/bin:$PATH"
version=3.9.11
base="https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/$version"
mkdir -p .tools .m2
if [ ! -x ".tools/apache-maven-$version/bin/mvn" ]; then
  curl --fail --location --silent --show-error "$base/apache-maven-$version-bin.tar.gz" -o .tools/maven.tar.gz
  curl --fail --location --silent --show-error "$base/apache-maven-$version-bin.tar.gz.sha512" -o .tools/maven.sha512
  expected=$(tr -d '[:space:]' < .tools/maven.sha512)
  actual=$(shasum -a 512 .tools/maven.tar.gz | cut -d ' ' -f 1)
  [ "$expected" = "$actual" ] || { echo 'Maven 다운로드 검증 실패'; exit 1; }
  tar -xzf .tools/maven.tar.gz -C .tools
  rm .tools/maven.tar.gz .tools/maven.sha512
fi
exec ".tools/apache-maven-$version/bin/mvn" -Dmaven.repo.local="$PWD/.m2" "$@"
