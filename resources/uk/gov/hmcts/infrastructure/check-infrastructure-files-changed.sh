#!/bin/bash
set -ex

git fetch origin master:master

changes=$(git diff origin/master --name-status infrastructure/)

if [ ! -z "$changes" -a "$changes" != " " ];
then
  exit 1
fi