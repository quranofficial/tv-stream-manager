# TV Stream Manager

Telegram bot for browsing the supplied 225-channel catalogue and returning candidate HLS audio/video URLs for opening in an external player such as VLC.

## Setup
1. Create a Telegram bot with BotFather and copy its token.
2. In this repository open Settings → Secrets and variables → Actions → New repository secret.
3. Add a secret named `BOT_TOKEN`. Never commit the token.
4. Open Actions → Telegram TV Bot → Run workflow.
5. Open the bot in Telegram and send `/start`.

Commands: `/start`, `/search channel name`, `/status`, `/stop`, `/restart`, `/logs`.

## URL limitation
The supplied channel JSON contains catalogue IDs, names and API play-url endpoints, but not the CDN `unit/<stream_key>` values. The bot currently guesses a candidate key from the display name. The only exact example supplied so far is Astera Family → `astera_family`. Verify each generated URL. Add exact mappings to `stream_keys.json`, keyed by channel ID, e.g. `{"113":"astera_family"}`.

Use only streams your subscription permits. Do not bypass provider authentication or share private account/session tokens.

## Hosting limitation
GitHub Actions is not a guaranteed 24/7 server. This workflow runs up to 350 minutes and is scheduled every six hours, so restarts and gaps can occur; GitHub may also delay scheduled workflows. The bot returns links only and does not relay video/audio.
