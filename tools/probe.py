import re, requests, base64, urllib.parse
from bs4 import BeautifulSoup
UA="Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
S=requests.Session(); S.headers["User-Agent"]=UA; S.headers["Accept-Language"]="vi-VN,vi;q=0.9,en;q=0.8"
print("==== AUTHOR")
r=S.get("https://www.wattpad.com/v4/search/stories",params={"query":"tình yêu","limit":3,"fields":"stories(id,user(name,username))"},timeout=20)
print(r.text[:400])
try: uname=r.json()["stories"][0]["user"].get("username") or r.json()["stories"][0]["user"]["name"]
except Exception as e: uname="antinh28"
for u in [f"https://www.wattpad.com/api/v3/users/{uname}/stories?limit=100&fields=stories(id,title)",
          f"https://www.wattpad.com/api/v3/users/{uname}/stories?limit=50&fields=stories(id,title,cover,description,user(name),numParts,readCount,completed,url)",
          f"https://www.wattpad.com/api/v3/users/{uname}/stories?limit=20",
          f"https://www.wattpad.com/api/v3/users/{uname}/stories/published?limit=20&fields=stories(id,title),total",
          f"https://www.wattpad.com/v4/users/{uname}/stories/published?limit=20&fields=stories(id,title),total",
          f"https://www.wattpad.com/api/v3/users/{uname}?fields=username,name,numStoriesPublished",
          f"https://www.wattpad.com/v4/search/users?query={uname}&limit=5&fields=username,name"]:
    r=S.get(u,timeout=20); print("AUTH", r.status_code, u, "::", r.text[:300])

def show_links(name, html, base):
    soup=BeautifulSoup(html,"html.parser")
    print(f"  [{name}] title={soup.title.string if soup.title else None!r} len={len(html)}")
    return soup
q="Cưng Chiều"
print("==== DDG HTML")
r=S.get("https://html.duckduckgo.com/html/",params={"q":f"{q} site:wattpad.com"},timeout=20); print(r.status_code)
soup=show_links("ddg",r.text,r.url)
for res in soup.select(".result")[:4]: print("   RES", re.sub(r"\s+"," ",str(res))[:700])
print("==== DDG LITE")
r=S.post("https://lite.duckduckgo.com/lite/",data={"q":f"{q} site:wattpad.com"},timeout=20); print(r.status_code)
soup=show_links("lite",r.text,r.url)
for a in soup.select("a.result-link")[:4]: print("   A", a.get("href"), a.get_text(strip=True)[:80])
print("   snippets", [x.get_text(" ",strip=True)[:100] for x in soup.select(".result-snippet")[:2]])
print("==== BING")
r=S.get("https://www.bing.com/search",params={"q":f"{q} site:wattpad.com","setlang":"vi","cc":"VN"},timeout=20); print(r.status_code)
soup=show_links("bing",r.text,r.url)
for li in soup.select("li.b_algo")[:4]:
    a=li.select_one("h2 a"); href=a.get("href") if a else None
    real=None
    m=re.search(r"[?&]u=a1([^&]+)",href or "")
    if m:
        b=m.group(1); b+= "="*(-len(b)%4)
        try: real=base64.urlsafe_b64decode(b).decode()
        except Exception as e: real=f"ERR {e}"
    cite=li.select_one("cite"); p=li.select_one("p, .b_caption p, .b_lineclamp2")
    print("   LI", href[:120] if href else None, "->", real, "|", a.get_text(strip=True)[:60] if a else None, "| cite:", cite.get_text(strip=True)[:80] if cite else None, "| p:", p.get_text(" ",strip=True)[:80] if p else None)
print("   b_algo count", len(soup.select("li.b_algo")), "raw wattpad links", len(re.findall("wattpad.com/", r.text)))
print("==== BING multi-site")
r=S.get("https://www.bing.com/search",params={"q":"tắt đèn (site:vietnamthuquan.eu OR site:wattpad.com)"},timeout=20)
print(r.status_code, len(BeautifulSoup(r.text,"html.parser").select("li.b_algo")))
