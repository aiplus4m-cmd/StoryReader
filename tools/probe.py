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

# ---------------- TruyenFull
r = get("https://truyenfull.vision/tim-kiem/?tukhoa=tien+nghich")
if r:
    s = outline(r.text, 120, ".list-truyen") ; base = r.url.split("/tim-kiem")[0]
    ls = [l for l in links(s, r"^https?://[^/]+/[a-z0-9-]+/$") if "the-loai" not in l and "danh-sach" not in l]
    if ls:
        r2 = get(ls[0])
        if r2:
            s2 = outline(r2.text, 200, "#truyen") 
            outline(r2.text, 60, "#list-chapter")
            print("  total-page:", re.findall(r'id="total-page"[^>]*', r2.text))
            ch = links(s2, r"chuong-\d+")
            if ch:
                r3 = get(ch[0])
                if r3: outline(r3.text, 40, "#chapter-big-container") ; print(r3.text[r3.text.find('id="chapter-c"'):][:1500])
            r4 = get(ls[0].rstrip("/") + "/trang-2/")
            if r4: print(r4.url); links(BeautifulSoup(r4.text, "html.parser"), r"chuong-\d+", 3)

# ---------------- VietMessenger
for u in ["http://vietmessenger.net/", "https://vietmessenger.net/"]:
    r = get(u)
    if r and r.ok:
        s = outline(r.text, 150)
        links(s, r".", 80)
        break

# ---------------- TangThuVien
for u in ["https://truyen.tangthuvien.vn/", "https://tangthuvien.vn/", "https://www.tangthuvien.vn/", "https://truyen.tangthuvien.net/"]:
    r = get(u)
    if r and r.ok:
        links(BeautifulSoup(r.text, "html.parser"), r"doc-truyen", 10); break

# ---------------- Wattpad
for u in [
    "https://www.wattpad.com/v4/search/stories?query=love&limit=2&fields=stories(id,title,url,user(name)),total",
    "https://www.wattpad.com/v4/search/stories/?query=love&limit=2&mature=1&fields=stories(id,title,url,user(name)),total",
    "https://api.wattpad.com/v4/search/stories?query=love&limit=2&fields=stories(id,title,url,user(name)),total",
    "https://www.wattpad.com/v4/search/stories?query=t%C3%ACnh%20y%C3%AAu&limit=2&fields=stories(id,title,url,user(name),parts(id)),total",
    "https://www.wattpad.com/api/v3/stories?query=love&limit=2&fields=stories(id,title,url)",
    "https://www.wattpad.com/api/v3/stories/2?fields=id,title,parts(id,title,url)",
    "https://www.wattpad.com/api/v3/stories/226474?fields=id,title,user(name),parts(id,title,url)",
]:
    r = get(u, headers={"Accept": "application/json"})
    if r: print("  ", r.text[:600])
r = get("https://www.wattpad.com/search/love")
if r: links(BeautifulSoup(r.text, "html.parser"), r"/story/", 5); print(re.findall(r'api[^"\']{0,80}search[^"\']{0,120}', r.text)[:10])
