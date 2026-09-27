# NZBDroid

A small Android app that does three things, nothing more:

1. **Queue** — controls a single NZBGet server (queue, history, pause/resume/delete, add by URL).
2. **Search** — searches multiple Newznab-compatible indexers at once and sends results
   straight to NZBGet.
3. **Files** — browses one saved SMB/Samba share and uploads/downloads files to/from your phone.

Kotlin + Jetpack Compose, Android only, minSdk 26.

## Building it

**Easiest: let GitHub Actions build it for you.** Push this folder to a new GitHub repo
and the included workflow (`.github/workflows/android-ci.yml`) builds a debug APK on
every push and attaches it to the run under **Actions → (latest run) → Artifacts**.
Download it, transfer to your phone, and install (you'll need to allow "install from
unknown sources" once). Trigger it manually any time from the **Actions** tab → "Android
CI" → **Run workflow**, without needing to push a change.

**Or locally in Android Studio:** open the `nzbdroid/` folder (Hedgehog/2023.1+), let it
sync, then Build → Run on a device or emulator (API 26+). The Gradle wrapper (jar +
scripts) is included and real, so `./gradlew assembleDebug` also works from a terminal if
you have the Android SDK installed.

## ⚠️ About this code

This project was written in a sandboxed environment with **no access to the internet
except a small allowlist** (npm/pip registries, GitHub) — it could not reach Google's
Maven repo, Maven Central, or the Gradle Plugin Portal, so **the app itself has never
actually been compiled**. What I could do instead:

- Every file was re-read line by line by hand after writing it (this caught and fixed
  two real bugs — a missing import and some leftover dead code).
- NZBGet's actual JSON-RPC field names/method signatures and jcifs-ng's actual class
  constructors were looked up against their real docs/source (both reachable via GitHub/
  web search) rather than written from memory, specifically to avoid hallucinated APIs.
- The Gradle wrapper jar and scripts are the **real, unmodified files** fetched directly
  from Gradle's GitHub repo (not fabricated), matching the 8.9 version pinned in
  `gradle-wrapper.properties`.

The GitHub Actions workflow above is the first real compile this code will go through.
If it fails, that's expected-and-fine, not a sign of a deeply broken project — paste me
the failing step's log and I'll fix the specific lines. Likely candidates: a dependency
version that's since been superseded (especially `androidx.security:security-crypto`,
still in alpha, and the exact Compose BOM patch number), both one-line fixes.

## Setup once it's running

Open the app → **Settings**:

- **NZBGet server** — host/IP, port (default `6789`), and the `ControlUsername` /
  `ControlPassword` from your `nzbget.conf`. "Test connection" before saving.
- **Indexers** — add one or more Newznab-compatible sources (NZBGeek, NZBHydra2,
  DrunkenSlug, a private tracker's Newznab API, etc.), each with its base URL and API key.
  Search fans out to all enabled indexers in parallel; a result you tap shows size/date/
  indexer with a **Download** button that sends it straight to NZBGet.
- **SMB share** — host/IP, share name, username/password, and an optional starting
  folder. The Files tab lets you browse, upload (share icon in the corner), and download
  (per-file button, saved to your phone's Downloads folder).

## Project layout

```
app/src/main/java/com/owan/nzbdroid/
├── data/
│   ├── settings/    NzbGetConfig, IndexerConfig, SmbConfig + encrypted-prefs repository
│   ├── nzbget/      JSON-RPC client (status/queue/history/append/editqueue/pause)
│   ├── indexer/      Newznab RSS/XML client + multi-indexer fan-out search
│   └── smb/          jcifs-ng wrapper (list/download/upload/delete)
└── ui/
    ├── queue/        Queue tab
    ├── search/       Search tab
    ├── files/        Files (SMB) tab
    └── settings/     Settings hub + 3 sub-screens
```

No Hilt/Dagger — a tiny hand-rolled `AppContainer` holds the one shared repository.
No ViewModel classes — each screen keeps its own `remember`-based state, which is
simpler for an app this size and refetches on re-entry (which is the behavior you want
for a queue/search screen anyway).

## Known limitations / things to add later

- SMB is single-share only (matches what you asked for); multi-share support would mean
  adding a server picker before the folder browser.
- No push notifications when a download finishes (nzb360 has this) — would need a
  background poll via WorkManager.
- No dark-mode-specific icon/theming polish beyond Material You dynamic color.
- Search results aren't cached; re-searching always hits the indexers again.
