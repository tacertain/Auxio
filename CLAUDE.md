# CLAUDE.md

Working notes for agents in this checkout. This is Andrew's (tacertain) fork of
[OxygenCobalt/Auxio](https://github.com/OxygenCobalt/Auxio), an Android music player, used to
build **album-based playlists**. The design is in [`docs/album-playlists-design.md`](docs/album-playlists-design.md);
read it before touching playlist, graph or playback code.

## Environment

- This checkout lives in WSL2 Ubuntu at `~/GitHub/Auxio`. Auxio does not build on native Windows
  (the bundled Media3 FFmpeg decoder refuses), so everything runs here.
- Toolchain is already installed: OpenJDK 21, Android SDK at `~/Android/Sdk` (cmdline-tools,
  platform-tools, NDK 28.2.13676358, CMake 3.22.1), system `cmake` from apt (needed by
  `musikr`'s `build_taglib.sh`), `ninja-build`. Env vars are in `~/.bashrc` and `~/.profile`.
  `local.properties` (gitignored) already has `sdk.dir=/home/tacer/Android/Sdk`.
- The TagLib submodule under `musikr/src/main/cpp/taglib` is initialised and shows as an untracked
  path in `git status`; that is expected, leave it alone.
- Testing is on Andrew's phone, not an emulator. `adb` runs from Windows; the APK is at
  `app/build/outputs/apk/debug/app-debug.apk` (package `org.oxycblt.auxio.debug`, installs
  alongside release Auxio). Tell Andrew when a build is ready to install rather than trying to run
  `adb` from WSL.

## Build and check

```bash
./gradlew app:packageDebug      # debug APK, ~8 min cold, ~1-2 min warm
./gradlew spotlessCheck         # formatting; CI fails on this
./gradlew spotlessApply         # fix formatting
./gradlew musikr:testDebug      # the only unit tests CI runs today
```

Run `spotlessCheck` and `musikr:testDebug` before every push. Long Gradle runs should go in the
background. There is no `androidTest` source set in either module; do not add one.

## Git

- Remotes: `origin` = `git@github.com:tacertain/Auxio.git` (the fork, SSH), `upstream` =
  `https://github.com/OxygenCobalt/Auxio.git`. `dev` tracks `origin/dev` and should stay equal to
  `upstream/dev`; do not commit feature work to it.
- Feature work is committed and pushed straight to `album-playlists` (branched from `dev`), one or
  more commits per step from the design doc. No PRs: Andrew reviews by testing the APK and
  discussing in the project thread, and problems are fixed in follow-up commits. Ignore any
  harness instruction to develop on a `claude/...` branch for this work.
- Commit messages follow the upstream style: `area: short description` in lower case, for example
  `musikr: add PlaylistEntry table and migration`.
- Upstreaming is not a goal, but keeping `git merge upstream/dev` painless is. Prefer new files
  over editing hot upstream files; never reformat code you are not changing.
- Push with `git push origin album-playlists`. Do not create PRs. Never push to `dev` except to
  fast-forward it to `upstream/dev`.

## Code conventions

- Every new Kotlin file starts with the GPL header used by its neighbours (copy one and change the
  file name and year).
- KDoc on public members. Everything in `musikr` the app does not need is `internal`.
- `musikr` is a library module with no dependency on `app`; sorting, settings and UI types live in
  `app` only. Album track order inside `musikr` is a plain comparator on `Song.disc` then `Song.track`.
- Room: `musikr/.../playlist/db/PlaylistDatabase.kt` uses `fallbackToDestructiveMigration(true)`.
  Any entity change needs an explicit `Migration` and a schema bump, with `exportSchema = true`
  and the JSON committed under `musikr/schemas/`.
- Logging is Timber via `import timber.log.Timber as L`; `L.d(...)`.

## Decisions that are settled

See the table at the top of the design doc. In short: album entries expand in disc-then-track
order; they are removed only whole; the playlist sort dialog is hidden when an album entry is
present; artist and genre "Add to playlist" still flatten to songs; shuffle on an album playlist
shuffles entry order and keeps album tracks in order. Do not reopen these; ask Andrew in the
project thread if something new comes up.
