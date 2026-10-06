#!/usr/bin/env bash
# Công cụ dò cấu trúc HTML của các nguồn truyện (chỉ dùng khi phát triển).
UA="Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
p() {
  echo "=================== $1"
  curl -sSL -m 25 -A "$UA" -o /tmp/p.html -w "HTTP %{http_code} final=%{url_effective}\n" "$1" || return
  echo "size: $(wc -c < /tmp/p.html)"
  python3 - "$2" <<'PY'
import re,sys
h=open('/tmp/p.html',encoding='utf-8',errors='replace').read()
mode=sys.argv[1] if len(sys.argv)>1 else ''
t=re.search(r'<title>(.*?)</title>',h,re.S); print("TITLE:",t.group(1).strip()[:150] if t else None)
if mode=='raw':
    body=re.sub(r'<script.*?</script>|<style.*?</style>|<svg.*?</svg>','',h,flags=re.S)
    body=re.sub(r'\n\s*\n+','\n',body)
    print(body[:9000])
else:
    links=re.findall(r'<a[^>]+href="([^"]+)"[^>]*>(.*?)</a>',h,re.S)
    for u,tx in links[:120]:
        print("  ",u[:120],"|",re.sub(r'<[^>]+>|\s+',' ',tx).strip()[:60])
    print("CLASSES:", sorted(set(re.findall(r'class="([^"]+)"',h)))[:200])
PY
}
p "https://vietmessenger.net/"
p "https://vietmessenger.net/" raw
p "https://truyenfull.vision/tim-kiem/?tukhoa=tien+nghich" raw
p "https://truyenfull.vision/tien-nghich/" raw
p "https://truyenfull.vision/tien-nghich/chuong-1/" raw
p "https://truyen.tangthuvien.vn/ket-qua-tim-kiem?term=dau+pha" raw
p "https://tangthuvien.net/" 
p "https://doctruyen.net.vn/"
p "https://dtruyen.net/"
p "https://www.wattpad.com/v4/search/stories?query=love&limit=2&fields=stories(id,title,cover,description,user(name),numParts,url),total" raw
p "https://www.wattpad.com/api/v3/stories/1?fields=id,title" raw
