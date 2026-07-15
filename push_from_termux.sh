#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
if [ "$#" -ne 1 ]; then
  echo "Usage: $0 https://github.com/USER/REPOSITORY.git"
  exit 1
fi
pkg install git -y
git init
git branch -M main
git add .
git commit -m "BMIB v0.3"
git remote remove origin 2>/dev/null || true
git remote add origin "$1"
git push -u origin main
