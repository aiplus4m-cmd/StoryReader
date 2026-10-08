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


print("=================== TRUYENC JS")
r=get("https://truyenc.com/static/js/main.min.js?v=0.0.7")
if r:
    print(re.findall(r'.{0,160}(?:search|keyword|tim-kiem|/api/|ajax|fetch\().{0,200}', r.text, re.I)[:20])
r=get("https://truyenc.com/truyen/cuu-bien-lien-78")
if r:
    soup=BeautifulSoup(r.text,"html.parser")
    print("  PAGINATION", [ (a.get("href"), a.get_text(strip=True)) for a in soup.select(".pagination a, #storyChapSidebar a, a[href*=page]")][:20])
    sb=soup.select_one("#storyChapSidebar"); print("  SIDEBAR", str(sb)[:1500] if sb else None)
    print("  INLINE", re.findall(r'.{0,120}(?:storyChap|loadChap|page=|chapters).{0,200}', r.text)[:12])
    d=soup.select_one(".card.card-full-left .content"); print("  INFO HTML", re.sub(r"\s+"," ",str(d))[:2500] if d else None)
r=get("https://truyenc.com/truyen/cuu-bien-lien/chuong-1-dan-truyen-2390")
if r:
    soup=BeautifulSoup(r.text,"html.parser"); c=soup.select_one(".story-content"); print("  CONTENT HTML", str(c)[:1200])
    print("  TITLE", [x.get_text(" ",strip=True) for x in soup.select(".page-title, .card-style .content h1, .card-style .content h2, .card-style .content h3, .card-style .content h4")][:6])

print("=================== LMVN")
for u in ["https://lmvn.com/truyen/index.php?func=search&keyword=kim%20dung", "https://lmvn.com/truyen/index.php?func=search&keyword=tam%20quoc"]:
    r=get(u)
    if r:
        ls=analyze(r, r"func=(?!main|tacgia|favorite|register)", 5)
        soup=BeautifulSoup(r.text,"html.parser")
        a=soup.find("a", href=re.compile(r"func=(view|story|doc|read)"))
        if a: print("  RESULT CTX", re.sub(r"\s+"," ",str(a.find_parent("tr") or a.parent))[:1500])
r=get("https://lmvn.com/truyen/index.php", method="POST", data={"func":"search","CODE":"0","keyword":"kim dung","searchin":"0"})
if r: analyze(r, r"func=(?!main|tacgia|favorite|register)", 5)
r=get("https://lmvn.com/truyen/index.php?func=main&cat=6")
if r:
    soup=BeautifulSoup(r.text,"html.parser")
    ls=[a["href"] for a in soup.find_all("a",href=True) if re.search(r"func=(?!main|tacgia|favorite|register|search)", a["href"])]
    print("  CAT LINKS", ls[:30])
    for u in ls:
        if "func=viewstory" in u or "func=story" in u or "id=" in u:
            r2=get(requests.compat.urljoin(r.url,u)); 
            if r2:
                ch=analyze(r2, r"func=|id=", 20)
                s2=BeautifulSoup(r2.text,"html.parser")
                print("  STORY HTML SNIP", re.sub(r"\s+"," ",s2.get_text(" ",strip=True))[:1500])
                cands=[x for x in ch if x!=r2.url and ("chap" in x.lower() or "chuong" in x.lower() or "page" in x.lower() or "viewstory" in x.lower())]
                print("  CANDS", cands[:10])
                if cands:
                    r3=get(cands[0]); 
                    if r3: analyze(r3, r"func=", 10)
            break
