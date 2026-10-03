# VideoPlayerSJ

Video player for a folder of downloaded shows and movies. Point it at a folder and episodes are
grouped into shows automatically, with thumbnails, watch progress and "continue watching".

Available for Android and for desktop (Windows, plus macOS/Linux from source).

## Download the latest build

Every push to `master` automatically builds fresh copies via GitHub Actions:

https://github.com/Amez73/VideoPlayerSJ/releases/latest

- **Android:** on your phone, open the link above in a browser and download `app-debug.apk`.
  You may need to allow "install unknown apps" for your browser in Android settings the first time.
- **Windows:** download `VideoPlayerSJ-Windows-Installer.msi` and double-click it, or grab
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
| F11 | Fullscreen (anywhere in the app) |

### Controller and couch mode

The whole app can be used without a mouse. In the library and show pages the arrow keys move
between items, Enter opens or plays, Esc goes back, and the Menu key (or Shift+F10) opens a
video's options.

Controllers work directly, with no Steam needed: PlayStation (DualSense, DualShock 4), Xbox,
Switch Pro and most other USB/Bluetooth pads. Buttons are listed as Xbox / PlayStation:

| Button | Library / show page | Player |
| --- | --- | --- |
| D-pad / left stick | Move | ← → skip 10 seconds, ↑ ↓ volume |
| A / Cross | Open / play | Play / pause (plays the next episode once one finishes) |
| B / Circle | Back, close menu | Back to the library |
| Y / Triangle | Options (mark watched, show in folder) | Next episode |
| X / Square | | Cycle subtitles |
| LT / RT (L2 / R2) | | Back / forward 10 seconds |
| LB / RB (L1 / R1) | | Volume down / up |
| View / Create | | Mute |
| Start / Options | Fullscreen | Fullscreen |

**Couch mode** (the TV button in the library) keeps the whole app fullscreen and remembers it for
next time; a Quit button appears in the top bar while fullscreen.

To launch it from **Steam Big Picture**: in Steam, *Games → Add a Non-Steam Game to My Library*,
browse to `VideoPlayerSJ.exe` (installed to `%LOCALAPPDATA%\VideoPlayerSJ` by default), and
optionally put `--couch` in the shortcut's launch options to start in couch mode. Steam's default
"Gamepad" controller layout passes the controller straight through to the app.

Run from source with `./gradlew :desktop:run`. On Windows the build downloads libVLC itself; on
macOS/Linux install VLC first. The library is stored in `%APPDATA%\VideoPlayerSJ` (Windows),
`~/Library/Application Support/VideoPlayerSJ` (macOS) or `~/.local/share/VideoPlayerSJ` (Linux).
