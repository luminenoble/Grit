#!/bin/sh
set -u
cd /mnt/d/wsl-share/Grit
ADB=/mnt/d/professionalTools/AndroidStorage/sdk/platform-tools/adb.exe
OUT=docs/创建Rhine/交付/阶段07
"$ADB" shell am instrument -w -e package com.shub39.grit com.shub39.grit.debug.test/androidx.test.runner.AndroidJUnitRunner > "$OUT/logs/device-tests.txt" 2>&1
python3 "$OUT/回归运行.py" > "$OUT/logs/regression-runner.txt" 2>&1
python3 "$OUT/最终媒体采集.py" > "$OUT/logs/media-final-run.txt" 2>&1
python3 "$OUT/浏览录屏.py" after > "$OUT/logs/video-after.txt" 2>&1
python3 "$OUT/设备状态.py" restore > "$OUT/logs/device-restore.txt" 2>&1
"$ADB" logcat -d -t 4000 > "$OUT/logs/final-logcat.txt" 2>&1
