<div align="center">
  <img src="docs/kecleon.png" alt="KecleonMod" width="80"/>

# KecleonMod

Private Pokémon GO automation for rooted Android (ARM64) — catches, spins, research, transfers, and item cleanup from one APK. Built on SilphMod 1.9.3.

</div>

---

## Version History

| Version | Date | Summary |
|---------|------|---------|
| [**1.0.6**](#v106) | 2026-10-22 | 5 anti-detection patches · Patcher v1.4 · delay jitter · AgentBuild path-sync · crash fixes |
| [1.0.5](#v105) | 2026-10-21 | Accordion UI · multi-select throw quality · Curveball/AR Mode · Spin Gyms · auto-save fix |
| [1.0.4](#v104) | 2026-10-21 | Patcher v1.3 · full UI redesign · separate spin/catch delays · Keep Nundo · trainer name |
| [1.0.3](#v103) | 2026-10-21 | Patcher v1.2 · 4 new anti-detection patches · CRLF fix · hardcoded package name fix |
| [1.0.1](#v101) | 2026-10-21 | GUI patcher · randomised anti-detection strings · SHA-256 sync · engine rebuild step |
| [1.0.0](#v100) | 2026-10-21 | Clean SilphMod 1.9.3 base · all injection bugs fixed · first working build |

---

<a name="v106"></a>
## v1.0.6 — Anti-Detection + Patcher v1.4 + Delay Jitter

**2026-10-22 · Working**

5 hardcoded-string anti-detection patches, Patcher v1.4 thread/path randomisation per build, ±15% delay jitter on all action timing, and a compile-time `AgentBuild` architecture that keeps every asset path in sync automatically — eliminating the engine integrity and spawn-guard crashes from v1.0.5.

### Anti-Detection Patches

**1 — Thread Name Randomisation** · Thread names are readable from `/proc/<pid>/task/*/comm`. Three identifiable threads renamed; Patcher v1.4 rotates from pools on every build.

| Thread | Old | Example new |
|--------|-----|-------------|
| NativeEngineRunner stop | `rootcatch-native-stop` | `async-task-3` |
| NativeEngineRunner reader | `rootcatch-native-reader` | `io-worker-1` |
| FarmService farm | `rootcatch-farm` | `binder-pool-2` |

**2 — SharedPreferences Rename** · `rootcatch.xml` → `app_config.xml`. Kept stable across builds so user settings survive updates.

**3 — Agent Asset Path Randomisation** · Agent bundle was at the Frida-standard `engine/agent.js`. Patcher v1.4 picks a random dir (`opt`, `res`, `sys`, `lib`, `bin`, `cfg`, `var`) and filename (`service.bin`, `config.bin`, `runtime.bin`, `loader.bin`, `agent.bin`) on every build and moves the asset.

**4 — Delay Jitter ±15%** · All fixed action delays now pass through `jitter()` — breaks fixed-interval timing signatures.
```typescript
function jitter(ms: number): number { return Math.round(ms * (0.85 + Math.random() * 0.30)); }
```
Applied to `actionDelayMs`, `spinDelayMs`, `catchDelayMs` (7 call sites).

**5 — Notification Genericisation** · Channel ID `bg_service` / `"Background Service"` · Wake lock `app:bg` · No `"KecleonMod · "` title prefix · Logs dir `app-logs`.

### Patcher v1.4

Two new randomisation steps added to `tools/patch_build.py`:

| Step | Pool |
|------|------|
| Thread names (×3) | 5 names each |
| Agent dir + filename | 7 dirs × 5 files |
| App ID, version code, native hashes | unchanged from v1.3 |

### AgentBuild Path-Sync Architecture

After patcher renamed `engine/agent.js`, three files with hardcoded `"engine/"` crashed at runtime. Fix: `engine/build.cjs` now emits `PATH` and `DIR` into the generated `AgentBuild.java`. `FarmPolicy` exposes `AGENT_PATH` and `AGENT_DIR`. Every file reads the compile-time constant — permanently immune to patcher renames.

```java
static final String SHA256 = "...";
static final String PATH   = "lib/service.bin";  // new
static final String DIR    = "lib";               // new
```

### Bugs Fixed

| Error | Root cause |
|-------|-----------|
| `"Engine integrity check failed"` | `FarmSession` opened `engine/agent.js` after patcher renamed it; SHA mismatch |
| `"ENGINE: ENGINE/SPAWN-GUARD.SH"` | `CompatibilitySession` + `LogExporter` had literal `"engine/"` patcher never updated |

---

<a name="v105"></a>
<details>
<summary><strong>v1.0.5 — Accordion UI + Throw Quality + Spin Gyms + Auto-Save</strong> · 2026-10-21</summary>

Major UI overhaul with 5 collapsible accordion sections, multi-select throw quality, Curveball and AR Mode toggles, Spin Gyms, inline item management, and permanent settings auto-save.

### New Features

**Collapsible Accordion Sections** · Flat layout replaced with 5 independent sections (Catch, Spin, Transfer, Inventory, Extra). State remembered per section.

**Multi-Select Throw Quality** · 4 checkboxes: Normal / Nice / Great / Excellent. Multiple selections → random pick per throw. Default: Excellent only.

**Curveball Toggle** · Was always on. Now a checkbox (default on). Sets `Spinning` on `PokeballThrow`.

**AR Mode Toggle** · Default off. Sets `ExpertHandler = true` on `ARPlusEncounterValuesProto`. Probes once on first throw — failure surfaces as a status bar warning instead of silently breaking.

**Spin Gyms** · Runtime probe tries 7 candidate IL2CPP method names; caches first match. Independent of PokéStop spinning. Uses `FortSearch` RPC 101.

**Inline Item Management** · "X item types" dialog replaced with inline checkboxes + keep-amount fields grouped by category (Poké Balls, Potions/Revives, Berries, Evolution items, TMs, Consumables, Lure Modules).

### Bugs Fixed

- **Settings not saving on pause** — `saveDraft()` called `saveSettings()` which validated before writing; any validation failure silently dropped all changes. Split into `persistSettings()` (always writes) and `saveSettings()` (validates then calls persist). `saveDraft()` now calls `persistSettings()` directly.
- **AR Mode silent failure** — empty `try/catch` hid field-rename errors. Now probes once and emits `ar_mode_error` on failure.
- **"Select a feature" with Gyms only** — `FarmPolicy.validate()` didn't count `spinGyms` as a standalone feature. Fixed.
- **Gym spin ignored when AutoSpin off** — tick loop gated on `config.autoSpin` only. Fixed to `config.autoSpin || config.spinGyms`.

</details>

---

<a name="v104"></a>
<details>
<summary><strong>v1.0.4 — Patcher v1.3 + UI Redesign + New Engine Features</strong> · 2026-10-21</summary>

Full dark-theme UI redesign. Separate spin/catch delays. Keep Nundo (0/0/0). User-configurable trainer name shown live in-game. Fixed `ReferenceError: LABEL` crash on engine startup.

### Changes

**UI Redesign** · Dark card layout with stat tiles, section labels, status strip. Removed engine dropdown and Action Interval field. Excellent throw is now default.

**Separate Spin/Catch Delays** · `spinDelayMs` and `catchDelayMs` replace the single `actionDelayMs` for spins and catches (100–3000 ms each, default 150 ms). All other actions still use `actionDelayMs`.

**Keep Nundo** · Transfer protection for 0 Atk / 0 Def / 0 Sta Pokémon. Off by default. Wired through transfer verdict, session ledger, and preflight telemetry.

**Trainer Name** · User-configurable label (default: `KecleonBot`). Shown live in HUD, profile, friends list, notification, and session logs.

### Bug Fixed

`ReferenceError: 'LABEL' is not defined` on startup — `LABEL` was in `module.exports` of `game-tools.cjs` but had been moved inside `install()`. Removed from exports.

</details>

---

<a name="v103"></a>
<details>
<summary><strong>v1.0.3 — Patcher v1.2 + Bug Fixes</strong> · 2026-10-21</summary>

4 new anti-detection patches. Fixed "Injector isolation unavailable" (CRLF line endings from Python on Windows). Fixed hardcoded package name breaking Fix Error 12 after app ID randomisation.

### New in Patcher v1.2

**Loader env var names** · `SILPH_LOADER_SCOPE` and `SILPH_LOADER_CONTEXT_FD` randomised every run. `spawn-guard.sh` and `loader-adapter.c` updated together.

**Loader adapter tag** · Tag string (e.g. `readonly-apk-v3`) in `loader-adapter.c` JSON log and `LoaderState.java` constant randomised together every run.

**Application ID (toggleable)** · `applicationId` in `build.gradle.kts` can be set to a system-looking package name via a GUI checkbox. Unchecked by default — changing app ID requires uninstalling the previous build.

### Bugs Fixed

**"Engine: Injector isolation unavailable"** — `Path.write_text()` on Windows writes `\r\n`. The CRLF `spawn-guard.sh` packed into the APK caused `then\r` to be unrecognised by `mksh`, probe failed, `guardExit != 0` → isolation disabled. Fixed: `newline="\n"` on all `write_text()` calls; repo file converted to LF.

**"Location treatment requires the automatic engine"** — `NativeEngineRunner.enableNativeLocation()` had `"dev.rootcatch.ugphonetest"` hardcoded. After app ID randomisation the package didn't match. Fixed: `BuildConfig.APPLICATION_ID`.

</details>

---

<a name="v101"></a>
<details>
<summary><strong>v1.0.1 — GUI Patcher + Anti-Detection</strong> · 2026-10-21</summary>

One-click GUI patcher (`patch_build.bat`) that randomises anti-detection strings and builds the release APK. Fixed "Engine Injector integrity failed" when runtime SHA-256 checks weren't updated after binary patching.

### What Gets Patched

**memfd name** · Frida's `memfd_create()` name is one of the most common detection vectors. Patched in-place in both inject `.so` binaries; `loader-policy.c` updated to match. Pool of 10 names (e.g. `art-jit-zygote.so`, `system-runtime.so`).

**Shell namespace variable** · Local shell var in `ConnectionStrategy.java` spawn/probe commands (e.g. `silph_parent_ns`) renamed every run. Pool of 8 names.

**SHA-256 hashes** · After binary patching, all 5 hash locations updated: `build.gradle.kts` `exactChecks` map + `ConnectionStrategy` enum fields (2 per library).

**Engine rebuild** · Calls `node engine/build.cjs` before Gradle runs to guarantee a fresh agent bundle and `AgentBuild.java`.

### Bug Fixed

`"Engine Injector integrity failed"` — previous patcher updated hashes in `build.gradle.kts` only, not in the `ConnectionStrategy` enum. Runtime check failed. Fixed: patcher now updates all 4 enum SHA-256 values.

</details>

---

<a name="v100"></a>
<details>
<summary><strong>v1.0.0 — Base Build</strong> · 2026-10-21</summary>

First working build. No new features on top of SilphMod 1.9.3 — clean, buildable foundation.

**Game target:** Pokémon GO `0.433.1` · Unity `6000.3.22f1` IL2CPP · ARM64 · Android 10+ · Frida `17.22.1`/`17.23.3`

### Bugs Fixed (from broken base)

1. **SELinux loader policy** — wrong stage names and SELinux label in `loader-policy.c`. `LoaderState.observe()` strict-matched them and threw `"Invalid loader confirmation"`.
2. **Frida stdin broken** — `build.cjs` was renaming `rpc.exports.onFridaStdin` and `getFridaTerminalMode`, breaking Frida's built-in stdin delivery. Engine injected but never acted. Fixed: clean `build.cjs`.
3. **Loader SHA mismatch** — `ConnectionStrategy.LOADER_SHA256` hardcoded to old hash after recompile. Fixed; `syncLoaderHash` Gradle task keeps it in sync.
4. **APK signing** — required release keystore env var. Added debug keystore fallback.

</details>

---

## Features

| Feature | Notes |
|---------|-------|
| Autocatch | Nearest eligible target within read game range |
| Species filter | Pokédex thumbnails and search |
| Throw quality | Normal / Nice / Great / Excellent — multi-select, random pick |
| Curveball | Toggle (default on) |
| AR Mode | AR+ expert handler bonus; warns if field missing after update |
| Berries | Priority-ordered; confirms effect before throwing |
| PokéStop spins | Within 80 m spin range |
| Spin Gyms | Runtime method probe; independent of PokéStop spinning |
| Field research | Claims, catches encounters, discards incompatible tasks |
| Transfers | Session-only Pokémon with exact ID confirmation |
| Keep filters | Shiny · 100 IV · Nundo (0/0/0) · Dynamax · hatched · background · field research |
| Show IV names | Temporary in-inventory display |
| Extended manual transfer | Expands multi-select for special Pokémon |
| Fast gift opening | Reduces animation to 50 ms |
| Hide trainer name | Temporary HUD/profile display — account unchanged |
| Item bag cleanup | 31 items across 7 groups with per-type keep limits |
| Fix Error 12 | JNI mock-location treatment (optional) |
| Session limits | Max catches (default 4800) + max spins (default 2400) |
| Separate delays | Independent catch delay + spin delay (100–3000 ms) |
| Delay jitter | ±15% random variation on all action timing |
| Map recovery | Auto-restarts game after 5 min with no Pokémon in range |
| Statistics | Catches · flees · spins · transfers · XP · Stardust |
| Log export | Last 3 sessions as a ZIP with SHA-256 index |

---

## Building

**Requirements:** JDK 17+ · Android SDK 36 · NDK `27.2.12479018` · Node.js 22+ · Python 3.11+

**Option A — GUI Patcher (recommended)**
1. Double-click `patch_build.bat`
2. Click **Patch & Build**

**Option B — Command line**
```powershell
python tools/patch_build.py        # patch + build
python tools/patch_build.py --dry  # preview only, no writes
```

**Option C — Raw Gradle (no patching)**
```powershell
.\gradlew.bat clean :app:assembleRelease
```

Output: `app/build/outputs/apk/release/app-release.apk`

> `libsilph-loader.so` is compiled from `native/` each build. `librootcatch-inject.so` and `libsilph-spawn.so` are pre-built — run `python tools/prepare_native.py` to obtain them. `AgentBuild.SHA256` is auto-generated; never edit manually.

---

<details>
<summary><strong>Repo Structure</strong></summary>

```
KecleonMod/
├── app/                        # Android application (Java)
│   └── src/main/
│       ├── java/dev/rootcatch/ # Service, session, engine runner, UI
│       └── jniLibs/arm64-v8a/ # Pre-built Frida injector binaries
├── engine/
│   ├── src/                   # Agent TypeScript + CommonJS modules
│   └── location/              # JNI mock-location adapter
├── native/                    # loader-adapter.c + loader-policy.c
├── tools/                     # patch_build.py, prepare_native.py, etc.
├── docs/                      # Screenshots
├── patcher_gui.py             # GUI patcher (tkinter)
└── patch_build.bat            # Double-click launcher
```

Not tracked: `engine/arm64_v8-0.433.1/` (IL2CPP dump, 817 MB) · `app/build/` · `engine/node_modules/` · `*.jks` / `*.keystore`

</details>

---

## Credits

Built on [SilphMod](https://github.com/rootcatch/silphmod) by **rootcatch** — IL2CPP injection engine, Frida runtime, farming logic, and Android service architecture.

Third-party: [Frida](https://frida.re) (LGPL-2.0) · [frida-il2cpp-bridge](https://github.com/vfsfitvnm/frida-il2cpp-bridge) (MIT) · [frida-java-bridge](https://github.com/frida/frida-java-bridge) (MIT) · buffer / base64-js / ieee754 (MIT/BSD) · Pokémon artwork from [PokeAPI/sprites](https://github.com/PokeAPI/sprites)

[MIT License](LICENSE)
