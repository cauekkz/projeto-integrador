#!/bin/bash

git add .
git commit -m "$1"

if [ -n "$2" ]; then
    git push origin "$2"
else
    git push
fi