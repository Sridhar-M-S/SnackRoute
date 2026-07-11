#!/bin/bash
# This script re-normalizes the Git repository index to ensure that
# gradle-wrapper.jar is tracked strictly as a binary file, preventing
# line-ending corruption on checkouts or commits (e.g., in GitHub Actions).

echo "=== Fixing Git Wrapper and Line Endings ==="

# 1. Force Git to recognize .gitattributes rules and re-normalize the index
echo "Removing gradle-wrapper.jar from Git cache..."
git rm --cached gradle/wrapper/gradle-wrapper.jar

echo "Re-adding gradle-wrapper.jar with binary attributes..."
git add gradle/wrapper/gradle-wrapper.jar

# 2. Re-normalize all files matching .gitattributes configuration
echo "Re-normalizing repository line endings..."
git add --renormalize .

echo "=== Done! ==="
echo "Now commit and push these changes to GitHub:"
echo "  git commit -m 'Fix: Re-add gradle-wrapper.jar as binary and normalize line endings'"
echo "  git push"
