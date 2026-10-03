# VideoPlayerSJ

Video player for a folder of downloaded shows and movies. Point it at a folder and episodes are
grouped into shows automatically, with thumbnails, watch progress and "continue watching".

Available for Android and for desktop (Windows, plus macOS/Linux from source).

## Download the latest build

Every push to `master` automatically builds fresh copies via GitHub Actions:

https://github.com/Amez73/VideoPlayerSJ/releases/latest

- **Android:** on your phone, open the link above in a browser and download `app-debug.apk`.
  You may need to allow "install unknown apps" for your browser in Android settings the first time.
- **Windows:** download `VideoPlayerSJ-windows.msi` and run it, or grab
  `VideoPlayerSJ-windows-portable.zip`, unzip it anywhere and run `VideoPlayerSJ.exe`.
  VLC's playback engine is bundled, so nothing else needs installing.

## Desktop app

Built with Compose for Desktop, sharing the Android app's filename parsing and show grouping
(the `core` module). Playback and thumbnails use libVLC, so mkv, HEVC, AC3/DTS and embedded
subtitles all work.

- **Add folder** picks a folder to scan (subfolders included); **Rescan** picks up new files.
- Folders on a drive that's unplugged keep their videos and watch history until it's back.
- Right-click a video to mark it watched/unwatched or show it in Explorer.

Player keyboard shortcuts:

| Key | Action |
| --- | --- |
| Space / K | Play / pause |
| ← / J, → / L | Back / forward 10 seconds |
| ↑ / ↓ | Volume |
| M | Mute |
| F / Enter / double-click | Fullscreen |
| N | Next episode |
| Esc | Exit fullscreen, then back to the library |

Run from source with `./gradlew :desktop:run`. On Windows the build downloads libVLC itself; on
macOS/Linux install VLC first. The library is stored in `%APPDATA%\VideoPlayerSJ` (Windows),
`~/Library/Application Support/VideoPlayerSJ` (macOS) or `~/.local/share/VideoPlayerSJ` (Linux).
