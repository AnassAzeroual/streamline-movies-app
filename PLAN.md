# Streamline — Speed & Robustness Plan

Goal: real-world streaming-app grade open-to-play speed, peer acquisition, and resilience.
Architecture stays: libtorrent4j -> blocking Media3 `TorrentDataSource` -> ExoPlayer (no local proxy).

## Phase 1 — Peer acquisition (biggest speed lever)

1. **Shared tracker + DHT list** (`core/torrent/TrackerLists.kt`)
   - Expand from 8 hardcoded trackers to a curated ~40-list (udp first, https fallbacks).
   - Refresh DHT bootstrap nodes (drop dead `dht.aelitis.com`, add `router.bitcomet.com`, `dht.transmissionbt.com`).
   - Used by both `TorrentStreamEngine` and `TorrentDownloadManager` (dedupe duplication).
2. **Web seeds** — no code needed
   - Verified against libtorrent4j 2.1.0-39: there is no `handle.addWebSeed(...)` and no
     `TorrentInfo.webSeeds()`. Since libtorrent 2.x web seeds live inside the torrent info
     dictionary and are picked up automatically when the torrent is added, so a torrent that
     carries `url-list` seeds already gets CDN-like throughput for rare/old swarms. Documented in
     `TrackerLists.kt` so the next reader does not look for a missing call.
3. **Aggressive announce** stays as-is (`announce_to_all_trackers/tiers`, forced DHT/LSD announce).

## Phase 2 — Buffering & player tuning (time-to-first-frame + smoothness)

1. **Smarter initial buffer target** (`TorrentStreamEngine`)
   - Replace fixed 2 MB with `clamp(fileSize * 1%, 6 MB, 24 MB)`: enough to cover the container
     header plus a few seconds of media, without delaying the first frame on a big 4K file. The
     player's own 15–120 s buffer then keeps refilling while playback runs.
   - Keep `firstPiece` readiness requirement; keep stall/total timeouts (stall raised to 75 s so slow
     handshakes don't kill viable swarms).
2. **Bitrate-aware read-ahead** (`TorrentDataSource`)
   - Cache piece sizes in an `IntArray` on the stream descriptor (kills the per-read JNI `pieceSize` call).
   - Read-ahead window is ~32 MB worth of pieces (clamped 4–64) instead of a fixed 6 pieces, so 512 KB
     old rips and 16 MB 4K remuxes both stay ahead of the player.
3. **ExoPlayer tuning** (`PlayerScreen`)
   - `DefaultLoadControl`: 15 s min / 120 s max buffer, 2 s play / 5 s rebuffer thresholds,
     prioritize time over size.
   - `setSeekBackIncrementMs(10_000)`, `setSeekForwardIncrementMs(10_000)`.
4. **Merge head/tail wait reporting** — one Buffering phase with a `stage` label; tail pieces stay
   TOP_PRIORITY from the start (already done via `boostInitialWindow`).

## Phase 3 — Robustness

1. **Safe file selection** (`chooseVideoFile`)
   - Drop `avi` (ExoPlayer cannot demux it — guaranteed playback error).
   - Never fall back to "largest file of any type" (could be `.rar`/`.nfo`); fail with a clear message
     listing what the torrent contains.
2. **Session cooperation**
   - One shared bandwidth policy: when streaming is active, throttle the download session
     (`download_rate_limit` via `applySettings`) and restore when playback stops.
   - Upload caps: streaming session capped (512 KB/s), download session capped (1 MB/s) —
     prevents ACK starvation on asymmetric links.
3. **Playback position persistence** (`core/playback/PlaybackPositionStore.kt`)
   - Keyed by the magnet's info hash (a magnet without one simply gets no resume entry).
   - Saved every 5 s while playing and on `ON_STOP`; cleared when playback reaches the end.
   - Player seeks to the saved position before `prepare()`; entries under 30 s or past 95% are ignored.
   - Capped at 100 entries, oldest pruned first.
4. **Subtitle sidecars**
   - Select sibling subtitle files (`.srt/.vtt/.ass/.ssa`, name-stem / same-folder scoring, ≤ 4 MB,
     max 8) from the torrent and set their piece priority to DEFAULT alongside the video.
   - Generalize the data source to a `TorrentFileStream` descriptor (file index, offset, size, piece
     range, cached piece sizes); a routing data source serves the video for
     `streamline-torrent://local/...` and any sidecar for `streamline-torrent://subtitle/<index>`.
   - Sideload with `SingleSampleMediaSource` + `MergingMediaSource` bound to that same data source
     factory — the player's built-in subtitle loader cannot resolve the custom scheme.

## Phase 4 — Measurement (bottleneck detection)

1. **Phase timings**: metadata / handle / first-peer / first-piece / buffer-target / moov / first-frame,
   all logged with elapsed-ms from play() start.
2. **EventLogger** attached as `AnalyticsListener` in debug builds (load events, rebuffers, drops).
3. **In-player stats overlay** (toggle in the controls row): seeds, peers, download/upload rate,
   wanted-bytes progress, buffered-ahead time, video format, dropped frames. Polls `TorrentStatus`
   every 500 ms while visible; dropped frames come from an `AnalyticsListener`.
4. Expose `numSeeds` (not just `numPeers`) in `TorrentState.Buffering` for UI/diagnostics.

## Out of scope (noted for later)

- FFmpeg renderer extension (needs native build; unblocks AVI/DTS later).
- "Continue watching" catalog row UI (store is in place; UI is a separate feature).
- TMDB metadata, Chromecast, PiP, foreground-service background audio.

## Verification gates

- `gradlew :app:compileDebugKotlin` after each phase.
- `gradlew :app:testDebugUnitTest` + `gradlew :app:assembleDebug` at the end.
- No git commits — user reviews everything first.
