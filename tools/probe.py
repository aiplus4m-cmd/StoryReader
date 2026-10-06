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



import json
# ---------- DTruyen
r = get("https://dtruyen.club/?s=tien+nghich")
if r:
    s = outline(r.text, 120, "main") or None
    ls = [l for l in links(BeautifulSoup(r.text, "html.parser"), r"dtruyen\.club/[^/]+/$", 40) if "the-loai" not in l]
    print("STORY CAND", ls[:10])
for l in (ls if r else [])[:0]: pass
def first_story(r, host):
    soup = BeautifulSoup(r.text, "html.parser")
    for a in soup.select("h3 a, h2 a, .title a, a[title]"):
        h = a.get("href", "")
        if host in h and "the-loai" not in h and h.rstrip("/").count("/") == 3: return h
r2 = None
if r:
    u = first_story(r, "dtruyen.club"); print("FIRST", u)
    if u:
        r2 = get(u)
        if r2:
            outline(r2.text, 220, "main")
            ch = links(BeautifulSoup(r2.text, "html.parser"), r"chuong", 6)
            print("  AJAX:", re.findall(r'(admin-ajax[^"\']*|action[\'"]?\s*[:=]\s*[\'"][a-z_]+)', r2.text)[:10])
            print("  INPUTS:", re.findall(r'<input[^>]+>', r2.text)[:10])
            if ch:
                r3 = get(ch[0])
                if r3: outline(r3.text, 60, "main")

# ---------- VietNamThuQuan
for u in ["https://vietnamthuquan.eu/truyen/timkiem?chu=tat+den", "https://vietnamthuquan.eu/Truyen/TimKiem?chu=tat+den", "https://vietnamthuquan.eu/?chu=tat+den"]:
    r = get(u)
    if r and r.ok:
        soup = BeautifulSoup(r.text, "html.parser")
        tp = links(soup, r"/TacPham/", 10)
        if tp: break
print("  scripts:", re.findall(r'<script[^>]*src="([^"]+)"', r.text)[:10] if r else None)
r = get("https://vietnamthuquan.eu/TacPham/noi-buon-chien-tranh-3530/")
if r:
    outline(r.text, 250)
    print("  JS snippets:", re.findall(r'.{0,120}(?:ajax|\$\.post|fetch\(|chuong|noidung).{0,160}', r.text, re.I)[:15])
