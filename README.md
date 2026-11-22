# SportVip

Telegram bot for browsing the supplied 595-channel sports catalogue and creating an on-demand combined audio/video stream for VLC.

## Setup
1. Create a Telegram bot with BotFather and copy its token.
2. In this repository open Settings → Secrets and variables → Actions → New repository secret.
3. Add a secret named `BOT_TOKEN`. Never commit the token.
4. Open Actions → Telegram TV Bot → Run workflow.
5. Open the bot in Telegram and send `/start`.
6. Select one channel. The bot returns one combined MPEG-TS URL for VLC.

Commands: `/start`, `/search channel name`, `/status`, `/stop`, `/restart`, `/logs`.

## How the relay works
The workflow installs FFmpeg and starts a temporary Cloudflare Quick Tunnel. When a user opens a channel's link in VLC, the runner starts FFmpeg on demand and muxes that channel's video and audio tracks into one MPEG-TS response. Each viewer connection creates its own FFmpeg process. Close the VLC stream when finished.

The public tunnel URL and stream access key are generated for each workflow run. Links stop working when the workflow ends, is cancelled, or reaches its 350-minute job limit. The tunnel URL changes on the next run. This is a temporary relay, not permanent hosting or a guaranteed 24/7 service.

## Channel key limitation
The sports catalogue contains channel IDs, names and API play-url endpoints. The exact HLS source is currently configured for BEIN SPORTS NEWS (ID 4353); other channels still require verified source URLs or exact CDN keys. The bot currently guesses a candidate key from the display name. The only exact mapping confirmed from the supplied example is BEIN SPORTS NEWS (ID 4353) has its supplied HLS URL configured as an explicit source. Other channels still use generated CDN-key candidates and must be tested individually. Add verified exact mappings to `stream_keys.json`, keyed by channel ID.

If a channel key is wrong or either source track is unavailable, VLC will not receive a valid combined stream. Do not attempt to bypass provider authentication or access controls. Use only streams your subscription permits, and never commit provider session tokens or private credentials.


## SportVip branding
The Telegram bot replies use the SportVip name. To change the actual Telegram display name shown in the chat header, use BotFather `/setname`; the bot username is changed separately with `/setusername`.
