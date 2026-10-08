import re, requests, urllib3, collections
from bs4 import BeautifulSoup
urllib3.disable_warnings()
S = requests.Session(); S.headers["User-Agent"] = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
def get(u, **kw):
    try:
        r = S.request(kw.pop("method","GET"), u, timeout=25, verify=False, **kw); print(f"\n######## {u} -> {r.status_code} {r.url} len={len(r.text)}"); return r
    except Exception as e: print(f"\n######## {u} -> ERR {e}")
KEYS=re.compile(r"title|author|tac-?gia|desc|intro|summary|chapter|chuong|list|content|story|truyen|book|cover|thumb|info|pag|result|item|search", re.I)
def sel(el):
    parts=[]
    while el is not None and getattr(el,"name",None) not in (None,"[document]","html","body"):
        s=el.name
        if el.get("id"): s+="#"+el["id"]
        elif el.get("class"): s+="."+".".join(el["class"][:2])
        parts.append(s); el=el.parent
    return " > ".join(reversed(parts[:5]))
def analyze(r, linkpat=None, maxkeys=70):
    soup=BeautifulSoup(r.text,"html.parser")
    for t in soup(["script","style","noscript","svg"]): t.decompose()
    seen=collections.OrderedDict()
    for el in soup.find_all(True):
        ident=" ".join(el.get("class",[]))+" "+(el.get("id") or "")
        if KEYS.search(ident):
            key=sel(el)
            if key in seen: seen[key][0]+=1; continue
            txt=el.get_text(" ",strip=True)[:90]
            attrs={k:el.get(k) for k in ("href","src","data-src","data-original","value") if el.get(k)}
            seen[key]=[1,txt,attrs]
    for k,(n,t,a) in list(seen.items())[:maxkeys]: print(f"  [{n}] {k} :: {t} {a if a else ''}")
    # biggest text block
    best=None
    for el in soup.find_all(["div","article","section","td"]):
        t=len("".join(x for x in el.find_all(string=True,recursive=False)).strip()) + sum(len(p.get_text()) for p in el.find_all("p",recursive=False))
        if best is None or t>best[0]: best=(t,el)
    if best: print("  BIGGEST TEXT:", sel(best[1]), best[0], "::", best[1].get_text(" ",strip=True)[:200])
    if linkpat:
        ls=[]
        for a in soup.find_all("a",href=True):
            if re.search(linkpat,a["href"]) and a["href"] not in [x[0] for x in ls]: ls.append((a["href"],a.get_text(" ",strip=True)[:50], sel(a)))
        print(f"  LINKS {linkpat} ({len(ls)}):")
        for l in ls[:15]: print("    ",l)
        return [requests.compat.urljoin(r.url,l[0]) for l in ls]
    return []

print("=================== TIEUTHUYET")
r=get("https://tieuthuyet.vn/search?q=yeu")
if r: analyze(r, r"tieuthuyet\.vn/(?!the-loai|danh-sach|search|login|review|tac-gia)[a-z0-9-]+$")
r=get("https://tieuthuyet.vn/search?q=yeu&page=2")
r=get("https://tieuthuyet.vn/chi-duoc-keo-cam-khong-duoc-yeu-duong")
if r:
    ch=analyze(r, r"/chuong-")
    print("  INPUTS", re.findall(r'<input[^>]+>', r.text)[:10])
    print("  AJAX", re.findall(r'.{0,80}(?:ajax|/api/|list-chapter|loadChapter|page=).{0,120}', r.text)[:10])
r=get("https://tieuthuyet.vn/chi-duoc-keo-cam-khong-duoc-yeu-duong/chuong-1")
if r: analyze(r, r"/chuong-\d+")

print("=================== TRUYENC")
r=get("https://truyenc.com/")
if r:
    print("  SCRIPTS", re.findall(r'<script[^>]*src="([^"]+)"', r.text)[:12])
    print("  INLINE", re.findall(r'.{0,100}(?:search|keyword|tim-kiem|fetch\(|axios|\$\.(?:get|post|ajax)).{0,160}', re.sub(r'<style.*?</style>','',r.text,flags=re.S))[:12])
    print("  INPUTS", re.findall(r'<input[^>]+>', r.text)[:10])
for u in ["https://truyenc.com/tim-kiem?q=ma","https://truyenc.com/search?keyword=ma","https://truyenc.com/tim-truyen?keyword=ma","https://truyenc.com/tim-truyen-ma"]:
    r=get(u)
    if r: analyze(r, r"/truyen/[a-z0-9-]+-\d+$", 25)
r=get("https://truyenc.com/truyen/cuu-bien-lien-78")
if r:
    analyze(r, r"/chuong-")
    print("  INPUTS", re.findall(r'<input[^>]+>', r.text)[:10])
r=get("https://truyenc.com/truyen/cuu-bien-lien/chuong-1-dan-truyen-2390")
if r: analyze(r, r"/chuong-", 30)

print("=================== LMVN")
r=get("https://lmvn.com/truyen/index.php")
if r:
    s2=BeautifulSoup(r.text,"html.parser")
    for f in s2.find_all("form"): print("FORM", re.sub(r"\s+"," ",str(f))[:700])
    i=r.text.find("function gosearch"); print(r.text[i:i+1200])
r=get("https://lmvn.com/truyen/index.php?func=main&cat=2")
if r: links=analyze(r, r"func=(?!main)|id=", 30)
