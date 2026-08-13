#!/usr/bin/env bash
# Automated Regression Test Runner for Khoi-Thu
set -e

echo "==========================================================="
echo "       Khoi-Thu Automated Regression Test Suite          "
echo "==========================================================="

MAVEN_CMD="mvn"
if command -v "C:\Users\tienl\.codex\tools\apache-maven-3.9.11\bin\mvn.cmd" &> /dev/null; then
    MAVEN_CMD="C:\Users\tienl\.codex\tools\apache-maven-3.9.11\bin\mvn.cmd"
fi

echo "[INFO] Running Maven unit and integration test suite..."
${MAVEN_CMD} test -pl novel-common,novel-front,novel-admin

echo "==========================================================="
echo "[SUCCESS] All regression tests passed successfully!"
echo "==========================================================="
