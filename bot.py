import json, logging, os, re
from pathlib import Path
from telegram import InlineKeyboardButton, InlineKeyboardMarkup, Update
from telegram.ext import Application, CommandHandler, CallbackQueryHandler, ContextTypes

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
log = logging.getLogger("tv-stream-manager")
DATA = json.loads(Path("channels.json").read_text(encoding="utf-8"))
CHANNELS = DATA.get("data", [])
PAGE_SIZE = 10
BASE = os.getenv("STREAM_BASE", "http://edge01.cdn.omxx.net/live/ku/ku/unit")
# Optional exact mappings: {"113": "astera_family"}
OVERRIDES = json.loads(Path("stream_keys.json").read_text(encoding="utf-8")) if Path("stream_keys.json").exists() else {}

def slug_for(channel):
    key = str(channel["id"])
    if key in OVERRIDES:
        return OVERRIDES[key]
    # Candidate only: provider path keys may not always match display names.
    s = channel["name"].lower().strip()
    s = re.sub(r"[^a-z0-9]+", "_", s).strip("_")
    return s

def channel_page(page=0, query=""):
    items = CHANNELS
    if query:
        q = query.casefold()
        items = [c for c in items if q in c["name"].casefold() or q in str(c.get("genre","")).casefold()]
    start = page * PAGE_SIZE
    part = items[start:start + PAGE_SIZE]
    rows = [[InlineKeyboardButton(c["name"], callback_data=f"ch:{c['id']}")] for c in part]
    nav = []
    if page > 0: nav.append(InlineKeyboardButton("⬅️ پێشوو", callback_data=f"page:{page-1}"))
    if start + PAGE_SIZE < len(items): nav.append(InlineKeyboardButton("➡️ زیاتر", callback_data=f"page:{page+1}"))
    if nav: rows.append(nav)
    return InlineKeyboardMarkup(rows), len(items)

async def start(update: Update, context: ContextTypes.DEFAULT_TYPE):
    context.user_data.clear()
    markup, total = channel_page()
    await update.message.reply_text(
        f"📺 بەخێربێیت! {total} کەناڵ بەردەستن. کەناڵێک هەڵبژێرە یان /search بنووسە.",
        reply_markup=markup)

async def search(update: Update, context: ContextTypes.DEFAULT_TYPE):
    q = " ".join(context.args).strip()
    if not q:
        await update.message.reply_text("بەکارهێنان: /search ناوی کەناڵ")
        return
    markup, total = channel_page(0, q)
    if not total:
        await update.message.reply_text("هیچ کەناڵێک نەدۆزرایەوە.")
    else:
        context.user_data["query"] = q
        await update.message.reply_text(f"ئەنجامەکان: {total}", reply_markup=markup)

async def buttons(update: Update, context: ContextTypes.DEFAULT_TYPE):
    q = update.callback_query
    await q.answer()
    if q.data.startswith("page:"):
        page = int(q.data.split(":")[1])
        markup, total = channel_page(page, context.user_data.get("query",""))
        await q.edit_message_reply_markup(reply_markup=markup)
        return
    if q.data.startswith("ch:"):
        cid = q.data.split(":")[1]
        ch = next((x for x in CHANNELS if str(x["id"]) == cid), None)
        if not ch:
            await q.message.reply_text("کەناڵ نەدۆزرایەوە.")
            return
        slug = slug_for(ch)
        video = f"{BASE}/{slug}/tracks-v1/index.fmp4.m3u8"
        audio = f"{BASE}/{slug}/tracks-a1/index.fmp4.m3u8"
        await q.message.reply_text(
            f"📺 {ch['name']}\n\n"
            f"🎥 Video (VLC):\n{video}\n\n"
            f"🔊 Audio (تەنها بۆ تاقیکردنەوە):\n{audio}\n\n"
            "⚠️ کلیلی URL لە ناوی کەناڵەوە بە شێوەی خۆکار پێشبینی کراوە؛ "
            "تەنها Astera Family لە نموونەی تۆدا پشتڕاست کراوەتەوە. "
            "ئەگەر لینک کار نەکرد، پێویستە stream_keys.json بە کلیلی ڕاستەقینە نوێ بکرێتەوە.",
            disable_web_page_preview=True)

async def status(update: Update, context: ContextTypes.DEFAULT_TYPE):
    await update.message.reply_text(f"🟢 بۆت چالاکە. ژمارەی کەناڵەکان: {len(CHANNELS)}")

async def stop(update: Update, context: ContextTypes.DEFAULT_TYPE):
    context.user_data.clear()
    await update.message.reply_text("ئەم گفتوگۆیە ڕاگیرا. بۆ دەستپێکردن /start بنووسە. (هیچ ستریمێک لە سێرڤەرەوە ناوەستێت؛ بۆت تەنها لینک دەدات.)")

async def restart(update: Update, context: ContextTypes.DEFAULT_TYPE):
    context.user_data.clear()
    await start(update, context)

async def logs(update: Update, context: ContextTypes.DEFAULT_TYPE):
    await update.message.reply_text("بۆ بینینی log ـەکان: GitHub → Actions → workflow run → Logs")

def main():
    token = os.getenv("BOT_TOKEN")
    if not token:
        raise RuntimeError("BOT_TOKEN is missing. Add it as a GitHub Actions secret.")
    app = Application.builder().token(token).build()
    app.add_handler(CommandHandler("start", start))
    app.add_handler(CommandHandler("search", search))
    app.add_handler(CommandHandler("status", status))
    app.add_handler(CommandHandler("stop", stop))
    app.add_handler(CommandHandler("restart", restart))
    app.add_handler(CommandHandler("logs", logs))
    app.add_handler(CallbackQueryHandler(buttons))
    log.info("Loaded %d channels", len(CHANNELS))
    app.run_polling(drop_pending_updates=True)

if __name__ == "__main__":
    main()
