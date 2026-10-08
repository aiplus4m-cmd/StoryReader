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



print("=================== TRUYENC SITEMAP")
r=get("https://truyenc.com/robots.txt")
if r: print(r.text[:800])
for u in ["https://truyenc.com/sitemap.xml","https://truyenc.com/sitemap_index.xml","https://truyenc.com/sitemap-truyen.xml"]:
    r=get(u)
    if r: print(r.text[:1500])
r=get("https://truyenc.com/tim-truyen-ma?page=2")
if r:
    soup=BeautifulSoup(r.text,"html.parser")
    for d in soup.select(".card.card-full-left .content .d-flex")[:2]: print("  ITEM", re.sub(r"\s+"," ",str(d))[:900])
for u in ["https://truyenc.com/tim-kiem?tu-khoa=ma","https://truyenc.com/tim-kiem?s=ma","https://truyenc.com/?s=ma","https://truyenc.com/tim-truyen?q=ma"]:
    r=get(u)
    if r: print("   has-result-list:", len(re.findall(r'/truyen/[a-z0-9-]+-\d+"', r.text)), "title:", re.findall(r'page-title page-title-fixed.{0,200}', r.text)[:1])

print("=================== LMVN")
r=get("https://lmvn.com/truyen/index.php?func=main&a=T")
if r:
    soup=BeautifulSoup(r.text,"html.parser")
    a=soup.find("a", href=re.compile("func=viewpost"))
    if a: print("  ROW", re.sub(r"\s+"," ",str(a.find_parent("table")))[:1500])
    print("  PAGES", [x["href"] for x in soup.find_all("a",href=True) if "page=" in x["href"]][:20])
    print("  COUNT viewpost", len(soup.find_all("a", href=re.compile("func=viewpost"))))
r=get("https://lmvn.com/truyen/index.php?func=viewpost&id=LY3PznfY1FvcA4KZXZCRVapMUIb6aDGy")
if r:
    i=r.text.find("Hồi 1 tiếp"); print("  AROUND CHAPLIST", r.text[i-3000:i+1500])
    print("  JS", re.findall(r'.{0,150}(?:ajax|xmlhttp|loadpage|viewpost|getpage|\.php\?).{0,200}', r.text, re.I)[:15])
r=get("https://lmvn.com/truyen/jscripts/ajax.js")
if r: print(r.text[:2500])
