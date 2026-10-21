# KecleonMod

Private Pokémon GO automation for rooted Android (ARM64).
Built on SilphMod 1.9.3 as the base engine — catches, spins, research, transfers, and item cleanup, all from one APK.

---

## Version History

| Version | Date | Tag | Summary |
|---------|------|-----|---------|
| [1.0.0](#v100--base-build) | 2026-10-21 | `Base Build` | Clean SilphMod 1.9.3 base. No redesign. Fixed all injection bugs. First working build. |

---

## v1.0.0 — Base Build

**Date:** 2026-10-21
**Status:** Working
**APK:** `app-release.apk` (signed with debug keystore)

### Overview
First working build of KecleonMod. No UI redesign, no new features on top of SilphMod 1.9.3. The goal was a clean, buildable, working foundation we can build future versions on.

### Game Target
| Component | Value |
|-----------|-------|
| Pokémon GO | `0.433.1` (`versionCode 2026101502`) |
| Unity | `6000.3.22f1`, IL2CPP |
| Architecture | ARM64 |
| Android | 10+ (`minSdk 29`, `targetSdk 36`) |
| Frida | `17.22.1` (attach) / `17.23.3` (early spawn) |

### Bugs Fixed (from broken base)

**1 — SELinux loader policy (`native/loader-policy.c`)**
The C loader was using wrong stage names and the wrong SELinux context label. Java's `LoaderState.observe()` does a strict match — unknown stage names silently dropped the confirmation and threw `"Invalid loader confirmation"`.
- `apk_context`: `frida_memfd` → `apk_data_file`
- Stage names: `original_obj` → `original_memfd`, `readonly_apk_fd` → `readonly_apk_memfd`
- Agent detection: loose ELF-header check → exact Frida agent name match

**2 — Frida stdin broken (`engine/build.cjs`)**
The build script had post-processing patches that renamed identifiers in the compiled agent bundle, including `rpc.exports.onFridaStdin` → `onSilphStdin` and `getFridaTerminalMode` → `getSilphTerminalMode`. Frida's runtime calls those by their original names to deliver stdin lines. After the rename, heartbeats, stop commands, and activation messages were silently dropped — the engine injected but never did anything.
- Fix: replaced with clean SilphMod 1.9.3 `build.cjs` (no patches)

**3 — Loader SHA256 mismatch (`ConnectionStrategy.java`)**
After recompiling `libsilph-loader.so` from the fixed C source, the hardcoded hash in `ConnectionStrategy.LOADER_SHA256` no longer matched. Fixed by updating to the hash of the freshly compiled binary.

**4 — APK signing**
Original setup required a release keystore environment variable. Added debug keystore fallback so the APK signs and installs without a release key.

**5 — Previous version conflict**
Any APK signed with a different key (old release.jks) must be uninstalled before installing a new build signed with the debug keystore.

### What's the Same as SilphMod 1.9.3
Everything. No UI changes, no feature additions, no engine modifications. This version is SilphMod 1.9.3 that builds and runs cleanly.

---

## Features

All features are from SilphMod 1.9.3. See below for a summary; future versions will track additions/changes per version.

| Feature | Notes |
|---------|-------|
| Autocatch | Nearest eligible target within read game range (up to 50 m) |
| Only catch | Species filter with thumbnails and Pokédex order |
| Throw quality | Normal / Nice / Great / Excellent with curveball |
| Berries | Priority-ordered; confirms effect before throwing |
| PokéStop spins | Within game range (80 m for spins) |
| Field research | Claims, catches encounters, discards incompatible tasks |
| Remove buddy | One-time per service start, triggers game restart |
| Transfers | Session-only Pokémon with exact ID confirmation |
| Keep filters | Shiny, 100% IV, Dynamax, hatched, background, shiny+background, field research |
| Show IV names | Temporary in-inventory display — account name unchanged |
| Extended manual transfer | Expands multi-select for special Pokémon |
| Fast gift opening | Reduces gift open animation to 50 ms |
| Hide trainer name | Displays temporary name in HUD/profile — account unchanged |
| Item bag cleanup | Per-type limits, from session start, no total quota |
| Fix Error 12 | JNI mock-location treatment, optional, per-session |
| Session limits | Max catches (default 4800) and spins (default 2400) |
| Map recovery | Auto-restarts game after 5 min with no Pokémon in range |
| Statistics | Catches, flees, spins, transfers, XP, Stardust — persistent until cleared |
| Log export | Last 3 sessions as a ZIP with SHA-256 index |

---

## Building

**Requirements:** JDK 17+, Android SDK 36, Android NDK `27.2.12479018`, Node.js 22+, Python 3.11+

```powershell
# Install engine dependencies
cd engine
npm.cmd ci
cd ..

# Build APK (loader compiles from C source automatically)
.\gradlew.bat clean :app:assembleRelease
```

Output: `app/build/outputs/apk/release/app-release.apk`

**Notes:**
- `libsilph-loader.so` is compiled from `native/` during each build — no manual step needed
- `librootcatch-inject.so` and `libsilph-spawn.so` are pre-built binaries checked in to `app/src/main/jniLibs/arm64-v8a/` and verified by SHA-256 at build time
- Gradle auto-generates `AgentBuild.SHA256` from the compiled `agent.js` — never edit it manually
- Debug keystore (`~/.android/debug.keystore`) is used for signing by default; set `SILPH_KEYSTORE` env var for a release key

---

## Repo Structure

```
KecleonMod/
├── app/                        # Android application (Java)
│   └── src/main/
│       ├── java/dev/rootcatch/ # Service, session, engine runner, UI
│       └── jniLibs/arm64-v8a/ # Pre-built Frida injector binaries
├── engine/
│   ├── src/                   # Agent TypeScript + CommonJS modules
│   └── location/              # JNI mock-location adapter
├── native/                    # loader-adapter.c + loader-policy.c (compiled to libsilph-loader.so)
├── tools/                     # Build helpers, item/Pokémon asset prep
├── .gitignore
├── build.gradle.kts
└── README.md
```

**Not tracked in git** (too large or auto-generated):
- `engine/arm64_v8-0.433.1/` — IL2CPP dump files (817 MB, local only)
- `app/build/`, `engine/node_modules/`, `.gradle/` — build outputs
- `*.jks`, `*.keystore`, `local.properties` — machine-specific / secrets

---

## Credits

KecleonMod is built on top of [SilphMod](https://github.com/rootcatch/silphmod) by **rootcatch**, which provides the core IL2CPP injection engine, Frida runtime integration, farming logic, and Android service architecture. Without that foundation this project would not exist.

**Third-party components used by the engine:**
- [Frida](https://frida.re) — dynamic instrumentation toolkit (LGPL-2.0 / wxWindows)
- [frida-il2cpp-bridge](https://github.com/vfsfitvnm/frida-il2cpp-bridge) — IL2CPP bridge for Frida (MIT)
- [frida-java-bridge](https://github.com/frida/frida-java-bridge) — Java bridge for Frida (MIT)
- [buffer](https://github.com/feross/buffer) / [base64-js](https://github.com/beatgammit/base64-js) / [ieee754](https://github.com/feross/ieee754) — utility modules (MIT / BSD)
- Pokémon artwork from [PokeAPI/sprites](https://github.com/PokeAPI/sprites)

See [LICENSE](LICENSE) and [NOTICE](NOTICE) for full license texts.

---

[MIT](LICENSE)
