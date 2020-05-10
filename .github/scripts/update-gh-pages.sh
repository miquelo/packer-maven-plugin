#!/bin/bash

# Try to do it through Maven
# https://maven.apache.org/guides/mini..
# ../guide-site.html#github-pages-apache-svnpubsub-gitpubsub-deployment

set -eu

REPO_URI="https://x-access-token:${GITHUB_TOKEN}@github.com"
REPO_URI="$REPO_URI/${GITHUB_REPOSITORY}.git"

REMOTE_NAME="origin"
MAIN_BRANCH="master"
TARGET_BRANCH="gh-pages"

pushd "$GITHUB_WORKSPACE" > /dev/null

git config user.name "$GITHUB_ACTOR"
git config user.email "${GITHUB_ACTOR}@bots.github.com"

mkdir .tmp
mv target/site/* .tmp

git checkout --orphan "$TARGET_BRANCH"

rm -rf * .github/ .gitignore
mv .tmp/* .
rm -rf .tmp
touch .nojekyll
git add --all .

git commit -m "Updating GitHub Pages"
if [ $? -ne 0 ]
then
  echo "Nothing to commit..."
  exit 0
fi

git remote set-url "$REMOTE_NAME" "$REPO_URI"
git push --force-with-lease "$REMOTE_NAME" "$TARGET_BRANCH"

popd > /dev/null
