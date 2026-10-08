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



APPF="stories(id,title,cover,description,user(name),numParts,url),total"
V={
 "APP(limit20,offset0,appfields)": {"limit":20,"offset":0,"fields":APPF},
 "limit20,no-offset": {"limit":20,"fields":F},
 "limit20,offset0": {"limit":20,"offset":0,"fields":F},
 "limit50,no-offset": {"limit":50,"fields":F},
 "limit50,offset0": {"limit":50,"offset":0,"fields":F},
 "limit50,offset0,mature1": {"limit":50,"offset":0,"mature":"1","fields":F},
 "limit20,offset0,mature1": {"limit":20,"offset":0,"mature":"1","fields":F},
 "limit20,mature1,no-offset": {"limit":20,"mature":"1","fields":F},
 "web(limit20 mature true offset0)": {"limit":20,"offset":0,"mature":"true","fields":F},
}
score={k:0 for k in V}; tots={k:[] for k in V}
for sd in seed[:20]:
    row=[]
    for k,p in V.items():
        pp=dict(p); pp["query"]=sd["title"]
        tot,st=ws(pp,k)
        pos=next((i for i,x in enumerate(st) if x["id"]==sd["id"]),None)
        if pos is not None: score[k]+=1
        row.append(f"{pos}/{tot}")
    print(f"- {sd['title'][:45]!r}: "+" | ".join(row))
for k in V: print(f"SCORE {score[k]:2d}/20  {k}")
