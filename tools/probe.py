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

variants = {
  "default(limit20)": lambda q: {"query":q,"limit":20,"fields":F},
  "mature=1": lambda q: {"query":q,"limit":20,"mature":"1","fields":F},
  "mature=true": lambda q: {"query":q,"limit":20,"mature":"true","fields":F},
  "mature=1,limit100": lambda q: {"query":q,"limit":100,"mature":"1","fields":F},
  "quoted,mature=1": lambda q: {"query":f'"{q}"',"limit":20,"mature":"1","fields":F},
  "folded,mature=1": lambda q: {"query":fold(q),"limit":20,"mature":"1","fields":F},
  "lang19,mature=1": lambda q: {"query":q,"limit":20,"mature":"1","language":"19","fields":F},
}
summary={k:0 for k in variants}
for s in seed[:20]:
    title=s["title"]
    line=[]
    for k,f in variants.items():
        tot,st=ws(f(title),k)
        rk=rank(st,title)
        if rk is not None and not str(rk).startswith("~"): summary[k]+=1
        line.append(f"{k}={rk}/{tot}")
    print(f"- {title!r} mature={s.get('mature')}:: "+" | ".join(line))
print("EXACT-FOUND SUMMARY", summary)

# Ngôn ngữ của truyện tiếng Việt
langs={}
for s in seed: l=(s.get("language") or {}).get("id"); langs[l]=langs.get(l,0)+1
print("LANG IDS", langs)

# v3 API
for q in [seed[0]["title"] if seed else "tình yêu"]:
    r=S.get("https://www.wattpad.com/api/v3/stories", params={"query":q,"limit":10,"mature":"1","fields":"stories(id,title)"}, timeout=20)
    print("V3", r.status_code, r.text[:400])
    r=S.get("https://www.wattpad.com/v4/search/autocomplete", params={"query":q[:8]}, timeout=20)
    print("AUTOCOMPLETE", r.status_code, r.text[:400])

# Web search fallback (DuckDuckGo HTML / Bing)
for s in seed[:4]:
    q=s["title"]
    for name,url,params in [
        ("ddg","https://html.duckduckgo.com/html/",{"q":f"site:wattpad.com {q}"}),
        ("bing","https://www.bing.com/search",{"q":f"site:wattpad.com/story {q}"}),
    ]:
        try:
            r=S.get(url,params=params,timeout=20)
            ids=re.findall(r'wattpad\.com(?:%2F|/)story(?:%2F|/)(\d+)', r.text)
            print(f"  WEB {name} {r.status_code} len={len(r.text)} q={q!r} ids={list(dict.fromkeys(ids))[:6]} want={s['id']} found={s['id'] in ids}")
        except Exception as e: print("  WEB",name,"ERR",e)
