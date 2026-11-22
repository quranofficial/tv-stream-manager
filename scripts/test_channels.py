import asyncio
import csv
import json
import re
from pathlib import Path

import aiohttp

BASE = "http://edge01.cdn.omxx.net/live/ku/ku/unit"
CHANNELS = json.loads(Path("channels.json").read_text(encoding="utf-8"))["data"]
OVERRIDES = json.loads(Path("stream_keys.json").read_text(encoding="utf-8")) if Path("stream_keys.json").exists() else {}
KNOWN = {
    "197": ["ava_4k"], "198": ["rudaw_4k"], "195": ["k24_4k"],
    "194": ["kurdsat_4k"], "183": ["channel_8_4k"], "192": ["nrt"],
    "191": ["nrt_2_hd"], "190": ["nrt_3_kids_hd"], "182": ["waar_4k"],
    "185": ["zagros_4k"], "180": ["kurdmax_sound2", "kurdmax"],
    "179": ["kurdmax", "kurdmax_sound2"],
    "178": ["kurdmax_show_sound2", "kurdmax_show"],
    "177": ["kurdmax_show", "kurdmax_show_sound2"],
}
SEM = asyncio.Semaphore(12)

def candidates(ch):
    cid = str(ch["id"])
    name = ch["name"].strip()
    low = re.sub(r"[^a-z0-9]+", " ", name.lower()).strip()
    keep = re.sub(r"[^A-Za-z0-9]+", " ", name).strip()
    generated = [low.replace(" ", "_"), low.replace(" ", ""), low.replace(" ", "-"),
                 keep.replace(" ", "_"), keep.replace(" ", ""), keep.replace(" ", "-")]
    return list(dict.fromkeys(x for x in ([OVERRIDES[cid]] if cid in OVERRIDES else [])
                              + KNOWN.get(cid, []) + generated if x))

async def manifest(session, url):
    try:
        timeout = aiohttp.ClientTimeout(total=12)
        async with session.get(url, timeout=timeout) as r:
            body = await r.content.read(4096)
            return r.status == 200 and b"#EXTM3U" in body, f"HTTP {r.status}"
    except asyncio.TimeoutError:
        return None, "manifest timeout"
    except Exception as e:
        return None, type(e).__name__

async def ffmpeg_test(url):
    cmd = ["ffmpeg", "-hide_banner", "-loglevel", "error", "-rw_timeout", "15000000",
           "-allowed_extensions", "ALL", "-extension_picky", "0", "-i", url,
           "-map", "0:v:0", "-map", "0:a:0", "-t", "4", "-c", "copy",
           "-f", "mpegts", "pipe:1"]
    try:
        p = await asyncio.create_subprocess_exec(*cmd, stdout=asyncio.subprocess.DEVNULL,
                                                 stderr=asyncio.subprocess.PIPE)
        _, err = await asyncio.wait_for(p.communicate(), timeout=28)
        if p.returncode == 0:
            return True, "FFmpeg 4-second audio/video test passed"
        return False, err.decode("utf-8", "replace").strip()[-700:] or f"ffmpeg exit {p.returncode}"
    except asyncio.TimeoutError:
        if p.returncode is None:
            p.kill()
            await p.wait()
        return None, "FFmpeg timeout"
    except Exception as e:
        return None, type(e).__name__

async def test_one(ch, session):
    cid, name = str(ch["id"]), ch["name"]
    notes = []
    saw_timeout = False
    async with SEM:
        for slug in candidates(ch):
            url = f"{BASE}/{slug}/tracks-v1a1/mono.ts.m3u8"
            ok, detail = await manifest(session, url)
            if ok is None:
                saw_timeout = True
                notes.append(f"{slug}: {detail}")
                continue
            if not ok:
                notes.append(f"{slug}: {detail}")
                continue
            passed, ffdetail = await ffmpeg_test(url)
            if passed:
                return {"id": cid, "name": name, "status": "WORKING",
                        "slug": slug, "url": url, "details": ffdetail}
            if passed is None:
                saw_timeout = True
            notes.append(f"{slug}: manifest OK, {ffdetail}")
    status = "UNKNOWN" if saw_timeout else "NOT_WORKING"
    return {"id": cid, "name": name, "status": status, "slug": "",
            "url": "", "details": " | ".join(notes[-8:]) or "No candidate source found"}

async def main():
    connector = aiohttp.TCPConnector(limit=24, ssl=False)
    headers = {"User-Agent": "Mozilla/5.0 TVStreamManager-ChannelAudit/1.0"}
    async with aiohttp.ClientSession(connector=connector, headers=headers) as session:
        results = await asyncio.gather(*(test_one(ch, session) for ch in CHANNELS))
    results.sort(key=lambda x: int(x["id"]))
    Path("channel-test-report.json").write_text(json.dumps(results, ensure_ascii=False, indent=2), encoding="utf-8")
    with open("channel-test-report.csv", "w", newline="", encoding="utf-8-sig") as f:
        w = csv.DictWriter(f, fieldnames=["id", "name", "status", "slug", "url", "details"])
        w.writeheader()
        w.writerows(results)
    counts = {s: sum(x["status"] == s for x in results) for s in ("WORKING", "NOT_WORKING", "UNKNOWN")}
    print(f"TOTAL={len(results)} WORKING={counts['WORKING']} NOT_WORKING={counts['NOT_WORKING']} UNKNOWN={counts['UNKNOWN']}")
    for x in results:
        print(f"{x['status']} | {x['id']} | {x['name']} | {x['details'][:240]}")
    if len(results) != 225:
        raise SystemExit(f"Expected 225 channels, got {len(results)}")

if __name__ == "__main__":
    asyncio.run(main())
