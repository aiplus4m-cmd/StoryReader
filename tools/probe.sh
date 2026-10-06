#!/usr/bin/env bash
# Công cụ dò cấu trúc HTML của các nguồn truyện (chỉ dùng khi phát triển).
pip install -q beautifulsoup4 requests >/dev/null 2>&1
python3 tools/probe.py
