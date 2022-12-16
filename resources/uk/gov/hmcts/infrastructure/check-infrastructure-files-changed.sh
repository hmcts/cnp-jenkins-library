#!/bin/bash
set -ex

git fetch origin master:master

git diff -s --exit-code master infrastructure/
