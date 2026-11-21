import asyncio
import aiohttp
import json
import logging
import os
import re
from pathlib import Path

from aiohttp import web
from telegram import InlineKeyboardButton, InlineKeyboardMarkup, Update
from telegram.ext import (
    Application, CallbackQueryHandler, CommandHandler, ContextTypes,
)

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
log = logging.getLogger("tv-stream-manager")

DATA = json.loads(Path("channels.json").read_text(encoding="utf-8"))
CHANNELS = DATA.get("data", [])
CHANNEL_BY_ID = {str(c["id"]): c for c in CHANNELS}
PAGE_SIZE = 10
BASE = os.getenv("STREAM_BASE", "http://edge01.cdn.omxx.net/live/ku/ku/unit")
PUBLIC_BASE = os.getenv("STREAM_PUBLIC_BASE", "").rstrip("/")
ACCESS_KEY = os.getenv("STREAM_ACCESS_KEY", "")
OVERRIDES = (
    json.loads(Path("stream_keys.json").read_text(encoding="utf-8"))
    if Path("stream_keys.json").exists() else {}
)


def slug_for(channel):
    cid = str(channel["id"])
    if cid in OVERRIDES:
        return OVERRIDES[cid]
    name = channel["name"].strip()
    lower_words = re.sub(r"[^a-z0-9]+", " ", name.lower()).strip()
    preserve_words = re.sub(r"[^A-Za-z0-9]+", " ", name).strip()
    candidates = (
        lower_words.replace(" ", "_"),
        lower_words.replace(" ", ""),
        lower_words.replace(" ", "-"),
        preserve_words.replace(" ", "_"),
        preserve_words.replace(" ", ""),
        preserve_words.replace(" ", "-"),
    )
    return next((x for x in candidates if x), "")


SLUG_CACHE = {}


async def _manifest_ok(session, url):
    try:
        timeout = aiohttp.ClientTimeout(total=10)
        async with session.get(url, timeout=timeout) as response:
            if response.status != 200:
                return False, response.status
            body = await response.content.read(4096)
            return b"#EXTM3U" in body, response.status
    except (aiohttp.ClientError, asyncio.TimeoutError) as exc:
        return False, type(exc).__name__


async def _ffmpeg_smoke_test(video_url, audio_url, cid, name, slug, muxed=False):
    common = ["ffmpeg", "-hide_banner", "-loglevel", "error", "-rw_timeout", "20000000",
              "-allowed_extensions", "ALL", "-extension_picky", "0", "-i"]
    if muxed:
        cmd = common + [video_url, "-map", "0:v:0", "-map", "0:a:0",
                        "-t", "4", "-c", "copy", "-f", "mpegts", "pipe:1"]
    else:
        cmd = common + [video_url, "-rw_timeout", "20000000",
                        "-allowed_extensions", "ALL", "-extension_picky", "0", "-i", audio_url,
                        "-map", "0:v:0", "-map", "1:a:0",
                        "-t", "4", "-c", "copy", "-f", "mpegts", "pipe:1"]
    try:
        proc = await asyncio.create_subprocess_exec(
            *cmd, stdout=asyncio.subprocess.DEVNULL, stderr=asyncio.subprocess.PIPE
        )
        _, stderr = await asyncio.wait_for(proc.communicate(), timeout=35)
        if proc.returncode == 0:
            log.info("FFmpeg test passed: channel=%s name=%s slug=%s mode=%s",
                     cid, name, slug, "muxed" if muxed else "separate")
            return True
        log.warning("FFmpeg test failed: channel=%s slug=%s mode=%s error=%s",
                    cid, slug, "muxed" if muxed else "separate",
                    stderr.decode("utf-8", errors="replace").strip()[-1000:])
        return False
    except (asyncio.TimeoutError, FileNotFoundError, OSError) as exc:
        log.warning("FFmpeg test error for channel %s slug=%s: %s", cid, slug, exc)
        if 'proc' in locals() and proc.returncode is None:
            proc.kill()
            await proc.wait()
        return False


def source_candidates(channel):
    cid = str(channel["id"])
    # Try muxed v1a1 first for every channel, then separate v1 + a1.
    known = {
        "197": ["ava_4k"], "198": ["rudaw_4k"],
        "195": ["k24_4k"], "194": ["kurdsat_4k"],
        "183": ["channel_8_4k"], "192": ["nrt"],
        "191": ["nrt_2_hd"], "190": ["nrt_3_kids_hd"],
        "182": ["waar_4k"], "185": ["zagros_4k"],
        "180": ["kurdmax_sound2", "kurdmax"],
        "179": ["kurdmax", "kurdmax_sound2"],
        "178": ["kurdmax_show_sound2", "kurdmax_show"],
        "177": ["kurdmax_show", "kurdmax_show_sound2"],
    }
    name = channel["name"].strip()
    lower_words = re.sub(r"[^a-z0-9]+", " ", name.lower()).strip()
    preserve_words = re.sub(r"[^A-Za-z0-9]+", " ", name).strip()
    generated = [
        lower_words.replace(" ", "_"), lower_words.replace(" ", ""),
        lower_words.replace(" ", "-"), preserve_words.replace(" ", "_"),
        preserve_words.replace(" ", ""), preserve_words.replace(" ", "-"),
    ]
    slugs = list(dict.fromkeys(
        slug for slug in list(known.get(cid, [])) + generated if slug
    ))
    for slug in slugs:
        yield slug, "muxed"
        yield slug, "separate"


async def resolve_source(channel):
    cid = str(channel["id"])
    if cid in SLUG_CACHE:
        return SLUG_CACHE[cid]
    async with aiohttp.ClientSession(
        headers={"User-Agent": "Mozilla/5.0 TVStreamManager/2.0"}
    ) as session:
        for slug, mode in source_candidates(channel):
            if mode == "muxed":
                url = f"{BASE.rstrip('/')}/{slug}/tracks-v1a1/mono.ts.m3u8"
                ok, status = await _manifest_ok(session, url)
                log.info("Source probe channel=%s slug=%s muxed=%s", cid, slug, status)
                if ok and await _ffmpeg_smoke_test(url, None, cid, channel["name"], slug, True):
                    result = {"mode": "muxed", "url": url, "slug": slug}
                    SLUG_CACHE[cid] = result
                    return result
            else:
                video = f"{BASE.rstrip('/')}/{slug}/tracks-v1/index.fmp4.m3u8"
                audio_options = [
                    f"{BASE.rstrip('/')}/{slug}/tracks-a1/index.fmp4.m3u8",
                ]
                vok, vs = await _manifest_ok(session, video)
                for audio in audio_options:
                    aok, ast = await _manifest_ok(session, audio)
                    log.info("Source probe channel=%s slug=%s video=%s audio=%s",
                             cid, slug, vs, ast)
                    if vok and aok and await _ffmpeg_smoke_test(video, audio, cid, channel["name"], slug):
                        result = {"mode": "separate", "video": video, "audio": audio, "slug": slug}
                        SLUG_CACHE[cid] = result
                        return result
    log.error("No working source found for channel %s (%s)", cid, channel["name"])
    return None


async def resolve_slug(channel):
    source = await resolve_source(channel)
    return source["slug"] if source else None


def channel_page(page=0, query=""):
    items = CHANNELS
    if query:
        q = query.casefold()
        items = [
            c for c in items
            if q in c["name"].casefold()
            or q in str(c.get("genre", "")).casefold()
        ]
    start = page * PAGE_SIZE
    part = items[start:start + PAGE_SIZE]
    rows = [[InlineKeyboardButton(c["name"], callback_data=f"ch:{c['id']}")] for c in part]
    nav = []
    if page > 0:
        nav.append(InlineKeyboardButton("⬅️ پێشوو", callback_data=f"page:{page-1}"))
    if start + PAGE_SIZE < len(items):
        nav.append(InlineKeyboardButton("➡️ زیاتر", callback_data=f"page:{page+1}"))
    if nav:
        rows.append(nav)
    return InlineKeyboardMarkup(rows), len(items)


async def stream_handler(request):
    if ACCESS_KEY and request.query.get("key") != ACCESS_KEY:
        raise web.HTTPForbidden(text="Invalid stream key")
    cid = request.match_info["channel_id"]
    channel = CHANNEL_BY_ID.get(cid)
    if channel is None:
        raise web.HTTPNotFound(text="Unknown channel")

    source = await resolve_source(channel)
    if not source:
        raise web.HTTPBadGateway(
            text="No tested working source was found for this channel. Check Actions logs."
        )
    if source["mode"] == "muxed":
        cmd = [
            "ffmpeg", "-hide_banner", "-loglevel", "error",
            "-rw_timeout", "20000000", "-allowed_extensions", "ALL",
            "-extension_picky", "0", "-i", source["url"],
            "-map", "0:v:0", "-map", "0:a:0", "-c", "copy",
            "-mpegts_flags", "+resend_headers", "-f", "mpegts", "pipe:1",
        ]
    else:
        cmd = [
            "ffmpeg", "-hide_banner", "-loglevel", "error",
            "-rw_timeout", "20000000", "-allowed_extensions", "ALL",
            "-extension_picky", "0", "-i", source["video"],
            "-rw_timeout", "20000000", "-allowed_extensions", "ALL",
            "-extension_picky", "0", "-i", source["audio"],
            "-map", "0:v:0", "-map", "1:a:0", "-c", "copy",
            "-mpegts_flags", "+resend_headers", "-f", "mpegts", "pipe:1",
        ]
    log.info("Starting combined relay for channel %s (%s)", cid, channel["name"])
    try:
        proc = await asyncio.create_subprocess_exec(
            *cmd, stdout=asyncio.subprocess.PIPE, stderr=asyncio.subprocess.PIPE
        )
    except FileNotFoundError:
        raise web.HTTPServiceUnavailable(text="FFmpeg is not installed")

    try:
        first = await asyncio.wait_for(proc.stdout.read(188 * 64), timeout=45)
        if not first:
            _, err = await asyncio.wait_for(proc.communicate(), timeout=10)
            detail = err.decode("utf-8", errors="replace").strip()[-1800:]
            log.error("FFmpeg failed for channel %s (%s): %s", cid, channel["name"], detail or "no stderr output")
            raise web.HTTPBadGateway(
                text="FFmpeg could not produce the combined stream. Check GitHub Actions logs for the FFmpeg error."
            )
        response = web.StreamResponse(
            status=200,
            headers={
                "Content-Type": "video/mp2t",
                "Cache-Control": "no-store, no-cache, must-revalidate",
                "Access-Control-Allow-Origin": "*",
                "X-Content-Type-Options": "nosniff",
            },
        )
        await response.prepare(request)
        await response.write(first)
        while True:
            chunk = await proc.stdout.read(188 * 256)
            if not chunk:
                break
            await response.write(chunk)
        await response.write_eof()
        return response
    except asyncio.TimeoutError:
        raise web.HTTPGatewayTimeout(text="Timed out waiting for the stream source")
    except (ConnectionResetError, asyncio.CancelledError):
        raise
    finally:
        if proc.returncode is None:
            proc.terminate()
            try:
                await asyncio.wait_for(proc.wait(), timeout=5)
            except asyncio.TimeoutError:
                proc.kill()
                await proc.wait()


async def health_handler(_request):
    return web.json_response({"status": "ok", "channels": len(CHANNELS)})


async def post_init(application: Application):
    server = web.Application()
    server.router.add_get("/stream/{channel_id}.ts", stream_handler)
    server.router.add_get("/health", health_handler)
    runner = web.AppRunner(server)
    await runner.setup()
    site = web.TCPSite(runner, "0.0.0.0", int(os.getenv("PORT", "8080")))
    await site.start()
    application.bot_data["http_runner"] = runner
    log.info("Combined stream HTTP server started on port %s", os.getenv("PORT", "8080"))


async def post_shutdown(application: Application):
    runner = application.bot_data.get("http_runner")
    if runner:
        await runner.cleanup()


async def start(update: Update, context: ContextTypes.DEFAULT_TYPE):
    context.user_data.clear()
    markup, total = channel_page()
    await update.message.reply_text(
        f"📺 بەخێربێیت! {total} کەناڵ بەردەستن. کەناڵێک هەڵبژێرە یان /search بنووسە.",
        reply_markup=markup,
    )


async def search(update: Update, context: ContextTypes.DEFAULT_TYPE):
    query = " ".join(context.args).strip()
    if not query:
        await update.message.reply_text("بەکارهێنان: /search ناوی کەناڵ")
        return
    markup, total = channel_page(0, query)
    if not total:
        await update.message.reply_text("هیچ کەناڵێک نەدۆزرایەوە.")
    else:
        context.user_data["query"] = query
        await update.message.reply_text(f"ئەنجامەکان: {total}", reply_markup=markup)


async def buttons(update: Update, context: ContextTypes.DEFAULT_TYPE):
    query = update.callback_query
    await query.answer()
    if query.data.startswith("page:"):
        page = int(query.data.split(":")[1])
        markup, _ = channel_page(page, context.user_data.get("query", ""))
        await query.edit_message_reply_markup(reply_markup=markup)
        return

    if query.data.startswith("ch:"):
        cid = query.data.split(":", 1)[1]
        channel = CHANNEL_BY_ID.get(cid)
        if not channel:
            await query.message.reply_text("کەناڵ نەدۆزرایەوە.")
            return
        if not PUBLIC_BASE:
            await query.message.reply_text(
                "تونێڵی گشتی ئامادە نییە. تکایە GitHub Actions log بپشکنە و workflow دووبارە Run بکە."
            )
            return
        await query.message.reply_text(
            f"⏳ سەرچاوەی {channel['name']} دەپشکنرێت؛ سەرەتا v1a1، پاشان v1+a1...",
            disable_web_page_preview=True,
        )
        source = await resolve_source(channel)
        if not source:
            await query.message.reply_text("❌ هیچ سەرچاوەیەکی کارا نەدۆزرایەوە.")
            return
        link = f"{PUBLIC_BASE}/stream/{cid}.ts?key={ACCESS_KEY}"
        if source["mode"] == "muxed":
            source_text = f"🔗 لینکی ڕاستەوخۆ (v1a1):\\n{source['url']}"
        else:
            source_text = ("🔗 ڤیدیۆ (v1):\\n" + source["video"] +
                           "\\n\\n🔊 دەنگ (a1):\\n" + source["audio"])
        await query.message.reply_text(
            f"✅ سەرچاوەی کارا: {channel['name']}\\n🧪 شێواز: {source['mode']}\\n\\n"
            f"{source_text}\\n\\n🎬 لینکی یەکگرتوو بۆ VLC:\\n{link}",
            disable_web_page_preview=True,
        )


async def status(update: Update, context: ContextTypes.DEFAULT_TYPE):
    await update.message.reply_text(
        f"🟢 بۆت چالاکە. ژمارەی کەناڵەکان: {len(CHANNELS)}"
    )


async def stop(update: Update, context: ContextTypes.DEFAULT_TYPE):
    context.user_data.clear()
    await update.message.reply_text(
        "گفتوگۆکە پاککرایەوە. بۆ دەستپێکردن /start بنووسە. "
        "ستریمەکانیش کاتێک VLC داخرا یان Workflow کۆتایی هات دەوەستن."
    )


async def restart(update: Update, context: ContextTypes.DEFAULT_TYPE):
    context.user_data.clear()
    await start(update, context)


async def logs(update: Update, context: ContextTypes.DEFAULT_TYPE):
    await update.message.reply_text(
        "بۆ بینینی log ـەکان: GitHub → Actions → Telegram TV Bot → workflow run → Logs"
    )


def main():
    token = os.getenv("BOT_TOKEN")
    if not token:
        raise RuntimeError("BOT_TOKEN is missing. Add it as a GitHub Actions secret.")
    if not PUBLIC_BASE:
        raise RuntimeError("STREAM_PUBLIC_BASE is missing. The Cloudflare tunnel did not start.")

    application = (
        Application.builder()
        .token(token)
        .post_init(post_init)
        .post_shutdown(post_shutdown)
        .build()
    )
    application.add_handler(CommandHandler("start", start))
    application.add_handler(CommandHandler("search", search))
    application.add_handler(CommandHandler("status", status))
    application.add_handler(CommandHandler("stop", stop))
    application.add_handler(CommandHandler("restart", restart))
    application.add_handler(CommandHandler("logs", logs))
    application.add_handler(CallbackQueryHandler(buttons))
    log.info("Loaded %d channels", len(CHANNELS))
    application.run_polling(drop_pending_updates=True)


if __name__ == "__main__":
    main()
