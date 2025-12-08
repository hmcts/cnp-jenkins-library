#!/bin/bash
set -x

USER_NAME=${1}
BEARER_TOKEN=${2}

git remote set-url origin $(git config remote.origin.url | sed "s/github.com/${USER_NAME}:${BEARER_TOKEN}@github.com/g")
git fetch origin master:master
git diff -s --exit-code master infrastructure/
