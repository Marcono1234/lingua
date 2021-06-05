#!/bin/bash
set -e # Exit on error

./gradlew clean accuracyReport -Pdetectors=Lingua

# Check for changes; https://stackoverflow.com/a/25149786
if [[ $(git status --porcelain) ]]; then
  # Get current commit hash; https://stackoverflow.com/a/949391
  commitSha=$(git rev-parse --verify HEAD)
  git add .
  git config --global user.email "action@github.com"
  git config --global user.name "GitHub Actions"
  git commit -m "Update accuracy reports" -m "Accuracy reports for $commitSha"
  
  # TODO: Not tested
  if [[ $(git push --porcelain | grep -q "! [rejected]") ]]; then
    echo "Failed pushing accuracy reports changes; new commit has probably been pushed in the meantime"
  else
    echo "Pushed accuracy reports changes"
  fi
else
  echo "No accuracy reports changes"
fi
