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

for home in ["https://tieuthuyet.vn/", "https://truyenc.com/", "https://lmvn.com/truyen/index.php"]:
    r=get(home)
    if r is not None:
        forms(r); links(r, 80)
