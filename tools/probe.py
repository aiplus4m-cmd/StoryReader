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


def jget(u):
    r = get(u, headers={"Accept": "application/json"})
    if r: print("  ", r.text[:1500])
    return r

# Wattpad detail + text
r = jget("https://www.wattpad.com/api/v3/stories/297837644?fields=id,title,description,cover,url,user(name),parts(id,title,url)")
try:
    pid = r.json()["parts"][0]["id"]
    t = get(f"https://www.wattpad.com/apiv2/storytext?id={pid}"); print(t.text[:800] if t else "")
except Exception as e: print("ERR", e)
jget("https://www.wattpad.com/v4/search/stories?query=love&limit=2&offset=0&fields=stories(id,title,cover,description,user(name),numParts,url),total")

# TruyenFull: big story pagination + content length
r = get("https://truyenfull.live/tien-nghich/")
if r:
    print("  total-page:", re.findall(r'id="total-page"[^>]*', r.text)); print("  pagination:", re.findall(r'href="([^"]*trang-\d+[^"]*)"', r.text)[:8])
r = get("https://truyenfull.live/tien-nghich/trang-3/")
if r: print("  ", re.findall(r'href="([^"]*chuong-\d+/)"', r.text)[:4])
r = get("https://truyenfull.live/tien-nghich/chuong-1/")
if r:
    soup = BeautifulSoup(r.text, "html.parser"); c = soup.select_one("#chapter-c")
    print("  content len", len(c.get_text()) if c else None, (c.get_text()[-300:] if c else ""))

# Candidates
for u in ["https://vietnamthuquan.eu/", "http://vietnamthuquan.eu/truyen/", "https://wetruyen.com/", "https://sstruyen.vn/", "https://truyenyy.mobi/",
          "https://metruyencv.com/", "https://backend.metruyencv.com/api/books/search?keyword=tien", "https://doctruyen.vip/", "https://truyenchu.com.vn/",
          "https://www.doctruyen.org/", "https://truyenhdt.com/", "https://truyen.com/", "https://dtruyen.club/", "https://truyenmoi.com/", "https://doctruyen.io/"]:
    r = get(u)
    if r is not None and r.ok:
        soup = BeautifulSoup(r.text, "html.parser")
        print("  TITLE", soup.title.string if soup.title else None)
        forms = [(f.get("action"), [i.get("name") for i in f.find_all("input")]) for f in soup.find_all("form")]
        print("  FORMS", forms[:4])
        links(soup, r".", 25)
