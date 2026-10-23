# Instagram Downloader (Android 16 / API 36)

Port of IGDownloader.py (PyQt6) to Kotlin + Jetpack Compose.

## Build
1. Open this folder in Android Studio (Ladybug or newer), let Gradle sync.
2. Run on a device/emulator (minSdk 26, targetSdk 36).

## Use
1. In Firefox, export your Instagram cookies with a "cookies.txt" extension (Netscape format).
2. Copy the file to your phone, tap **Import** in the app and pick it.
3. Paste (or share) an Instagram URL, choose format, tap **Download**.
4. Files land in Downloads/IGDownloader. History log: **Share history log**.
5. If downloads start failing, tap **Update yt-dlp**.

## Desktop -> Android mapping
- yt-dlp subprocess      -> youtubedl-android (bundled yt-dlp + Python + ffmpeg)
- ffmpeg GIF/MP3 steps   -> yt-dlp --recode-video gif (same filter chain) / -x --audio-format mp3
- gallery-dl (images)    -> OkHttp + Instagram media-info API with your cookies
- --cookies-from-browser -> --cookies <imported cookies.txt>
- instagram_download_history.log -> same "url|format" file in app storage
- Downloads folder       -> MediaStore Downloads/IGDownloader
- Clipboard monitor      -> checks clipboard on app focus + Android Share target
- QThread + Cancel       -> coroutines + destroyProcessById
