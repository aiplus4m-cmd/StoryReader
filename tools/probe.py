import re, json, requests, unicodedata, urllib.parse
from bs4 import BeautifulSoup
UA="Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
S=requests.Session(); S.headers["User-Agent"]=UA
def fold(s):
    s=unicodedata.normalize("NFD",s.lower()); s="".join(c for c in s if unicodedata.category(c)!="Mn").replace("đ","d")
    return re.sub(r"[^a-z0-9]+"," ",s).strip()
F="stories(id,title,user(name),mature,language(id),numParts,readCount),total,nextUrl"
def ws(params, label):
    try:
        r=S.get("https://www.wattpad.com/v4/search/stories", params=params, timeout=20, headers={"Accept":"application/json"})
        d=r.json(); st=d.get("stories",[])
        return d.get("total"), st
    except Exception as e:
        print("   ERR",label,e); return None,[]
def rank(st, title):
    t=fold(title)
    for i,s in enumerate(st):
        if fold(s["title"])==t: return i
    for i,s in enumerate(st):
        if t in fold(s["title"]): return f"~{i}"
    return None

# Lấy vài tiêu đề thật (bao gồm truyện mature) để thử
seed=[]
for q in ["ngôn tình", "đam mỹ", "xuyên không", "tổng tài", "hệ thống"]:
    for mature in ["1"]:
        tot,st=ws({"query":q,"limit":50,"offset":0,"mature":mature,"fields":F},"seed")
        for s in st[10:50:8]: seed.append(s)
print("SEED", [(s["title"], s.get("mature"), (s.get("language") or {}).get("id"), s.get("readCount")) for s in seed][:25])


def core(t):
    t=re.sub(r"[\[\(][^\]\)]*[\]\)]"," ",t)          # bỏ [..] (..)
    t=re.split(r"\s[-–|]\s",t)[0]                      # bỏ " - tác giả"
    return re.sub(r"\s+"," ",t).strip(" -:|")
def deep(q, want, pages=6, extra=None):
    for p in range(pages):
        params={"query":q,"limit":50,"offset":p*50,"mature":"1","fields":F}
        if extra: params.update(extra)
        tot,st=ws(params,"deep")
        for i,s in enumerate(st):
            if s["id"]==want: return p*50+i, tot
        if len(st)<50: break
    return None, tot
q0=seed[7]["title"] if len(seed)>7 else None
if q0:
    tot,st=ws({"query":q0,"limit":50,"mature":"1","fields":F},"x")
    print("RESULTS FOR", q0, tot, [x["title"] for x in st][:15])
found_full=found_core=0
for s in seed[:20]:
    t=s["title"]; c=core(t)
    a=deep(t,s["id"]); b=deep(c,s["id"]) if c and c!=t else ("same",None)
    if a[0] is not None: found_full+=1
    if b[0] not in (None,): found_core+=1
    print(f"- {t!r} core={c!r} full_pos={a} core_pos={b}")
print("FOUND within 300: full", found_full, "core", found_core)

# Trang tìm kiếm web
for s in seed[:3]:
    r=S.get("https://www.wattpad.com/search/"+urllib.parse.quote(s["title"]), timeout=20)
    ids=re.findall(r'"id":"?(\d{6,})"?,"title"', r.text)
    print("WEBPAGE", r.status_code, len(r.text), "found" if s["id"] in r.text else "notfound", ids[:5], re.findall(r'(?:api|v\d)/search[^"\' ]{0,120}', r.text)[:5])
# DDG không site:
for s in seed[:6]:
    for q in [f'wattpad "{core(s["title"])}"', f'{s["title"]} wattpad']:
        try:
            r=S.get("https://html.duckduckgo.com/html/",params={"q":q},timeout=20)
            ids=list(dict.fromkeys(re.findall(r'wattpad\.com(?:%2F|/)story(?:%2F|/)(\d+)', r.text)))
            print(f"  DDG {r.status_code} q={q!r} found={s['id'] in ids} ids={ids[:5]}")
        except Exception as e: print("DDG ERR",e)
