import ast
import asyncio
import csv
import json
import re
from pathlib import Path
import aiohttp
import urllib.parse

CHANNELS = json.loads(Path("channels.json").read_text(encoding="utf-8"))["data"]
TREE = ast.parse(Path("bot.py").read_text(encoding="utf-8"))
EXPLICIT = {}
for node in TREE.body:
    if isinstance(node, ast.Assign) and any(isinstance(t, ast.Name) and t.id == "EXPLICIT_SOURCES" for t in node.targets):
        EXPLICIT = ast.literal_eval(node.value)
        break
OVERRIDES = json.loads(Path("stream_keys.json").read_text(encoding="utf-8")) if Path("stream_keys.json").exists() else {}
BASE = "http://edge01.cdn.omxx.net/live/ku/ku/unit"
SEM = asyncio.Semaphore(12)

KNOWN = {
    "197":["ava_4k"],"198":["rudaw_4k"],"195":["k24_4k"],"194":["kurdsat_4k"],
    "183":["channel_8_4k"],"192":["nrt"],"191":["nrt_2_hd"],"190":["nrt_3_kids_hd"],
    "182":["waar_4k"],"185":["zagros_4k"],"180":["kurdmax_sound2","kurdmax"],
    "179":["kurdmax","kurdmax_sound2"],"178":["kurdmax_show_sound2","kurdmax_show"],
    "177":["kurdmax_show","kurdmax_show_sound2"]
}

def candidate_slugs(ch):
    cid=str(ch["id"])
    name=ch["name"].strip()
    low=re.sub(r"[^a-z0-9]+"," ",name.lower()).strip()
    return list(dict.fromkeys(([OVERRIDES[cid]] if cid in OVERRIDES else []) +
        KNOWN.get(cid,[]) + [x for x in (low.replace(" ","_"),low.replace(" ",""),low.replace(" ","-")) if x]))

async def manifest(session,url):
    try:
        async with session.get(url,timeout=aiohttp.ClientTimeout(total=12)) as r:
            body=await r.content.read(4096)
            return r.status==200 and b"#EXTM3U" in body, f"HTTP {r.status}"
    except Exception as e:
        return None,type(e).__name__

async def ffmpeg(urls):
    cmd=["ffmpeg","-hide_banner","-loglevel","error","-rw_timeout","15000000",
         "-allowed_extensions","ALL","-extension_picky","0"]
    for url in urls:
        if len(urls)>1 and url != urls[0]:
            cmd += ["-rw_timeout","15000000","-allowed_extensions","ALL","-extension_picky","0"]
        cmd += ["-i",url]
    cmd += ["-map","0:v:0","-map",f"{len(urls)-1}:a:0" if len(urls)>1 else "0:a:0",
            "-t","4","-c","copy","-f","mpegts","pipe:1"]
    try:
        p=await asyncio.create_subprocess_exec(*cmd,stdout=asyncio.subprocess.DEVNULL,stderr=asyncio.subprocess.PIPE)
        _,err=await asyncio.wait_for(p.communicate(),timeout=35)
        return p.returncode==0,err.decode("utf-8","replace")[-450:]
    except Exception as e:
        return None,type(e).__name__

def split_track_urls(muxed_url):
    """Derive v1/a1 from the exact same channel path and host."""
    parts=urllib.parse.urlsplit(muxed_url)
    marker="/tracks-v1a1/"
    if marker not in parts.path: return None
    base=parts.path.split(marker,1)[0].rstrip("/")
    video=urllib.parse.urlunsplit(parts._replace(path=base+"/tracks-v1/index.fmp4.m3u8"))
    audio=[
        urllib.parse.urlunsplit(parts._replace(path=base+"/tracks-a1/index.fmp4.m3u8")),
        urllib.parse.urlunsplit(parts._replace(path=base+"/tracks-a1/mono.ts.m3u8")),
    ]
    return video,audio

async def test_separate_urls(ch,video,audios):
    async with aiohttp.ClientSession(headers={"User-Agent":"Mozilla/5.0 TVStreamManager-Audit/2.0"}) as session:
        vok,vd=await manifest(session,video)
        if not vok: return False,"v1 unavailable: "+str(vd)
        for audio in audios:
            aok,ad=await manifest(session,audio)
            if not aok: continue
            passed,why=await ffmpeg([video,audio])
            if passed: return True,"same-channel v1+a1 FFmpeg passed"
            if passed is None: return None,why
        return False,"v1 exists but no matching a1 passed FFmpeg"

async def test_exact(ch,src):
    mode=src.get("mode","muxed")
    if mode=="separate":
        return await test_separate_urls(
            ch,src["video"],src.get("audio_options",[src["audio"]])
        )
    url=src["url"]
    async with aiohttp.ClientSession(headers={"User-Agent":"Mozilla/5.0 TVStreamManager-Audit/2.0"}) as session:
        ok,detail=await manifest(session,url)
    if ok:
        passed,why=await ffmpeg([url])
        if passed: return True,"complete v1a1 FFmpeg passed"
        if passed is None: return None,why
    split=split_track_urls(url)
    if split:
        passed,why=await test_separate_urls(ch,*split)
        if passed: return True,why
        if passed is None: return None,why
    return False,"v1a1 failed ("+str(detail)+"); same-source v1+a1 failed"

async def test_one(ch):
    cid=str(ch["id"]); name=ch["name"]
    async with SEM:
        if cid in EXPLICIT:
            ok,detail=await test_exact(ch,EXPLICIT[cid])
            if ok: return {"id":cid,"name":name,"status":"WORKING","url":EXPLICIT[cid].get("url",EXPLICIT[cid].get("video","")),"details":detail}
            return {"id":cid,"name":name,"status":"UNKNOWN" if ok is None else "NOT_WORKING",
                    "url":"","details":"Exact bot.py source: "+detail}
        notes=[]; unknown=False
        for slug in candidate_slugs(ch):
            muxed=f"{BASE}/{slug}/tracks-v1a1/mono.ts.m3u8"
            async with aiohttp.ClientSession(headers={"User-Agent":"Mozilla/5.0 TVStreamManager-Audit/2.0"}) as session:
                ok,detail=await manifest(session,muxed)
            if ok:
                passed,why=await ffmpeg([muxed])
                if passed:
                    return {"id":cid,"name":name,"status":"WORKING","url":muxed,"details":"complete v1a1 FFmpeg passed"}
                if passed is None: unknown=True
                notes.append("v1a1: "+why)
            elif ok is None:
                unknown=True; notes.append("v1a1: "+str(detail))
            split=split_track_urls(muxed)
            if split:
                passed,why=await test_separate_urls(ch,*split)
                if passed:
                    return {"id":cid,"name":name,"status":"WORKING","url":split[0]+" + "+next((a for a in split[1] if a in str(why)),split[1][0]),
                            "details":"same-slug v1+a1 FFmpeg passed"}
                if passed is None: unknown=True
                notes.append("v1+a1: "+str(why))
        return {"id":cid,"name":name,"status":"UNKNOWN" if unknown else "NOT_WORKING",
                "url":"","details":"; ".join(notes[-6:]) or "No valid same-channel source"}

async def main():
    results=await asyncio.gather(*(test_one(ch) for ch in CHANNELS))
    results.sort(key=lambda x:int(x["id"]))
    Path("channel-test-report.json").write_text(json.dumps(results,ensure_ascii=False,indent=2),encoding="utf-8")
    with open("channel-test-report.csv","w",newline="",encoding="utf-8-sig") as f:
        w=csv.DictWriter(f,fieldnames=["id","name","status","url","details"]); w.writeheader(); w.writerows(results)
    counts={s:sum(x["status"]==s for x in results) for s in ("WORKING","NOT_WORKING","UNKNOWN")}
    print(f"TOTAL={len(results)} WORKING={counts['WORKING']} NOT_WORKING={counts['NOT_WORKING']} UNKNOWN={counts['UNKNOWN']}")
    for x in results:
        if x["status"]!="WORKING": print(f"{x['status']} | {x['id']} | {x['name']} | {x['details']}")
    if len(results)!=len(CHANNELS): raise SystemExit(f"Expected {len(CHANNELS)} results, got {len(results)}")

if __name__=="__main__":
    asyncio.run(main())
