#!/bin/bash
set -x

USER_NAME=${1}
BEARER_TOKEN=${2}

git fetch origin master:master
git remote set-url origin $(git config remote.origin.url | sed "s/github.com/${USER_NAME}:${BEARER_TOKEN}@github.com/g")
git diff -s --exit-code master infrastructure/
