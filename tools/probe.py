import re, requests, urllib3
from bs4 import BeautifulSoup
urllib3.disable_warnings()
S = requests.Session(); S.headers["User-Agent"] = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
def get(u):
    try:
        r = S.get(u, timeout=25, verify=False); print(f"\n######## {u} -> {r.status_code} {r.url} len={len(r.text)} enc={r.encoding}"); return r
    except Exception as e: print(f"\n######## {u} -> ERR {e}")
def outline(html, maxlines=150, root=None):
    soup = BeautifulSoup(html, "html.parser")
    for t in soup(["script","style","svg","noscript","head"]): t.decompose()
    node = soup.select_one(root) if root else (soup.body or soup)
    if node is None: print("  (root not found)"); return
    n=[0]
    def walk(el,d):
        for c in el.children:
            if n[0]>maxlines: return
            if getattr(c,"name",None):
                at=[]
                for k in ("id","class","href","src","data-src","name","value","action"):
                    v=c.get(k)
                    if v: at.append(f'{k}="{(" ".join(v) if isinstance(v,list) else v)[:90]}"')
                own="".join(x for x in c.find_all(string=True,recursive=False)).strip()
                print("  "*min(d,16)+f"<{c.name} {' '.join(at)}> {own[:70]}"); n[0]+=1; walk(c,d+1)
    walk(node,0)
def forms(r):
    soup=BeautifulSoup(r.text,"html.parser")
    print("  FORMS", [(f.get("action"), f.get("method"), [(i.get("name"),i.get("type")) for i in f.find_all(["input","select"])]) for f in soup.find_all("form")][:5])
    print("  JS-search", re.findall(r'.{0,80}(?:search|tim-kiem|timkiem|keyword).{0,120}', r.text, re.I)[:6])
def links(r, k=60):
    soup=BeautifulSoup(r.text,"html.parser"); out=[]
    for a in soup.find_all("a",href=True):
        t=a.get_text(" ",strip=True)[:40]
        if a["href"] not in [o[0] for o in out]: out.append((a["href"],t))
    for h,t in out[:k]: print("   ",h[:110],"|",t)


def first(r, pat):
    soup=BeautifulSoup(r.text,"html.parser")
    for a in soup.find_all("a",href=True):
        if re.search(pat,a["href"]): return requests.compat.urljoin(r.url,a["href"])
# ---- tieuthuyet
r=get("https://tieuthuyet.vn/search?q=yeu")
if r:
    outline(r.text,120,"main") if BeautifulSoup(r.text,"html.parser").select_one("main") else outline(r.text,160)
    u=first(r, r"tieuthuyet\.vn/(?!the-loai|danh-sach|search|login|review)[a-z0-9-]+$")
    print("STORY",u)
    if u:
        r2=get(u); outline(r2.text,200)
        print(re.findall(r'.{0,100}(?:ajax|api/|chapter|chuong).{0,150}', r2.text)[:12])
        c=first(r2, r"chuong|chapter")
        print("CH",c)
        if c:
            r3=get(c); outline(r3.text,80)
# ---- truyenc
r=get("https://truyenc.com/")
if r:
    print(re.findall(r'<script[^>]*src="([^"]+)"', r.text)[:10])
    print(re.findall(r'.{0,120}(?:search|tim-kiem|keyword|\?q=).{0,160}', re.sub(r'<style.*?</style>','',r.text,flags=re.S), re.I)[:12])
for u in ["https://truyenc.com/tim-kiem?q=ma","https://truyenc.com/search?q=ma","https://truyenc.com/tim-kiem/ma"]:
    r=get(u)
u="https://truyenc.com/truyen/cuu-bien-lien-78"
r2=get(u)
if r2:
    outline(r2.text,200)
    c=first(r2, r"chuong|chap")
    print("CH",c)
    if c:
        r3=get(c); outline(r3.text,80)
# ---- lmvn
r=get("https://lmvn.com/truyen/index.php")
if r:
    i=r.text.find('name="searchMe"'); i=r.text.find("searchMe")
    print(r.text[i-1500:i+1500])
    s2=BeautifulSoup(r.text,"html.parser")
    for f in s2.find_all("form"): print("FORM", str(f)[:800])
r=get("https://lmvn.com/truyen/index.php?func=main&cat=2")
if r:
    st=first(r, r"func=(viewstory|view|story|doc)|id=")
    print("SAMPLE LINKS"); links(r,60)
