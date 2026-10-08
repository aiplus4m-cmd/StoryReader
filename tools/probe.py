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
    t=re.sub(r"[\[\(][^\]\)]*[\]\)]"," ",t); t=re.split(r"\s[-–|]\s",t)[0]
    return re.sub(r"\s+"," ",t).strip(" -:|,")
def pos_of(q, sid, extra=None, limit=50):
    p={"query":q,"limit":limit,"offset":0,"fields":F}
    if extra: p.update(extra)
    tot,st=ws(p,"x")
    return next((i for i,x in enumerate(st) if x["id"]==sid),None), tot
tests={"core":lambda t: core(t),"core-nodau":lambda t: fold(core(t)),"core-lower":lambda t: core(t).lower(),
       "3 tu dau":lambda t:" ".join(core(t).split()[:3]),"bo 1 tu":lambda t:" ".join(core(t).split()[1:]) if len(core(t).split())>2 else core(t)}
sc={k:[0,0] for k in tests}
for sd in seed[:20]:
    row=[]
    for k,f in tests.items():
        q=f(sd["title"]); p,tot=pos_of(q,sd["id"])
        if p is not None: sc[k][0]+=1
        if p is not None and p<5: sc[k][1]+=1
        row.append(f"{k}:{p}/{tot}")
    print(f"- {sd['title'][:40]!r}: "+" | ".join(row))
for k,(a,b) in sc.items(): print(f"SCORE {k}: trong top50={a}/20, top5={b}/20")

# Mature: tìm truyện mature thật
mat=[]
for q in ["cao h","sắc","h văn","np","18+"]:
    tot,st=ws({"query":q,"limit":50,"offset":0,"mature":"1","fields":F},"m")
    mat+= [x for x in st if x.get("mature")]
print("MATURE seeds", len(mat), [x["title"] for x in mat[:6]])
ok_def=ok_m=0
for x in mat[:10]:
    a=pos_of(x["title"],x["id"]); b=pos_of(x["title"],x["id"],{"mature":"1"})
    ok_def+= a[0] is not None; ok_m+= b[0] is not None
    print("  MATURE", x["title"][:40], "default:",a, "mature=1:",b)
print("MATURE found default", ok_def, "mature=1", ok_m)

# Link chương (part) -> truyện
if seed:
    r=S.get(f"https://www.wattpad.com/api/v3/stories/{seed[0]['id']}?fields=parts(id,url)",timeout=20)
    part=r.json()["parts"][2]
    for u in [f"https://www.wattpad.com/api/v3/story_parts/{part['id']}?fields=id,groupId,title",
              f"https://www.wattpad.com/v4/parts/{part['id']}?fields=id,group(id,title)"]:
        r=S.get(u,timeout=20); print("PART", u, r.status_code, r.text[:300])
