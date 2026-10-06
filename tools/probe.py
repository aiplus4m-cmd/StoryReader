import re, sys, requests, urllib3
from bs4 import BeautifulSoup
urllib3.disable_warnings()
UA = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
S = requests.Session(); S.headers["User-Agent"] = UA

def get(u, **kw):
    try:
        r = S.get(u, timeout=25, verify=False, **kw)
        print(f"\n######## {u} -> {r.status_code} {r.url} len={len(r.text)}")
        return r
    except Exception as e:
        print(f"\n######## {u} -> ERR {e}"); return None

def outline(html, maxlines=250, root=None):
    soup = BeautifulSoup(html, "html.parser")
    for t in soup(["script", "style", "svg", "noscript", "head"]): t.decompose()
    node = soup.select_one(root) if root else soup.body or soup
    if node is None: print("  (root not found)"); return soup
    n = 0
    def walk(el, d):
        nonlocal n
        for c in el.children:
            if n > maxlines: return
            if getattr(c, "name", None):
                attrs = []
                for k in ("id", "class", "href", "src", "data-src", "data-image", "itemprop", "value", "name", "data-id"):
                    v = c.get(k)
                    if v: attrs.append(f'{k}="{(" ".join(v) if isinstance(v, list) else v)[:90]}"')
                own = "".join(x for x in c.find_all(string=True, recursive=False)).strip()
                print("  " * min(d, 20) + f"<{c.name} {' '.join(attrs)}> {own[:70]}")
                n += 1
                walk(c, d + 1)
    walk(node, 0)
    return soup

def links(soup, pat, k=15):
    out = []
    for a in soup.find_all("a", href=True):
        if re.search(pat, a["href"]) and a["href"] not in out: out.append(a["href"])
    print("  LINKS", pat, out[:k]); return out




r = get("https://vietnamthuquan.eu/truyen/timkiem?chu=tat+den")
if r:
    soup = BeautifulSoup(r.text, "html.parser")
    a = soup.find("a", href=re.compile("/TacPham/tat-den-1959/"))
    p = a
    for _ in range(4):
        if p.parent: p = p.parent
    print("SEARCH ITEM CONTEXT:", str(p)[:2500])
r = get("https://vietnamthuquan.eu/TacPham/noi-buon-chien-tranh-3530/")
if r:
    i = r.text.find("MODAL"); print("MODAL:", r.text[i:i+3000])
    print("chuong links:", sorted(set(re.findall(r'/TacPham/noi-buon-chien-tranh-3530/chuong-\d+', r.text)))[:20])
    for k in ["totalchuong", "vntq-select-chuong", "<option"]:
        j = r.text.find(k); print(k, "=>", r.text[j-200:j+600] if j>=0 else None)
r = get("https://vietnamthuquan.eu/TacPham/noi-buon-chien-tranh-3530/chuong-2")
if r:
    soup = BeautifulSoup(r.text, "html.parser")
    print([x.get_text(strip=True) for x in soup.select(".chuongso_a, .vntq-reader-title")])
    c = soup.select_one("#vntqTextContent"); print("len", len(c.get_text()) if c else None)

for u in ["https://dtruyen.club/?s=tien", "https://dtruyen.club/tim-kiem/?tukhoa=tien", "https://dtruyen.club/"]:
    r = get(u)
    if r is not None and r.ok:
        soup = BeautifulSoup(r.text, "html.parser")
        st = [a["href"] for a in soup.find_all("a", href=True) if re.match(r"https://dtruyen\.club/[^/]+/$", a["href"]) and "the-loai" not in a["href"]]
        print("STORIES", st[:8])
        if st:
            r2 = get(st[0]); 
            if r2: outline(r2.text, 200, "main") ; print(re.findall(r'<input[^>]+>', r2.text)[:8]); print(re.findall(r'.{0,100}admin-ajax.{0,200}', r2.text)[:4])
            ch = [a["href"] for a in BeautifulSoup(r2.text,"html.parser").find_all("a", href=True) if "chuong" in a["href"]][:3]
            print("CH", ch)
            if ch:
                r3 = get(ch[0]); 
                if r3: outline(r3.text, 50, "main")
        break
