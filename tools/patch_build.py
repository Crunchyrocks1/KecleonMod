#!/usr/bin/env python3
"""
KecleonMod Patcher v1.1 -- Random-Patch & Build
  Randomises anti-detection strings, patches .so binaries in-place,
  updates integrity hashes, then assembles the release APK.

Usage:
  python tools/patch_build.py          # random patch + build
  python tools/patch_build.py --dry    # show what would change, no writes
"""

import argparse
import hashlib
import re
import random
import subprocess
import sys
from pathlib import Path

try:
    from colorama import init, Fore, Style
    init(autoreset=True)
    G   = lambda s: Fore.GREEN   + Style.BRIGHT + str(s) + Style.RESET_ALL
    Y   = lambda s: Fore.YELLOW  + Style.BRIGHT + str(s) + Style.RESET_ALL
    R   = lambda s: Fore.RED     + Style.BRIGHT + str(s) + Style.RESET_ALL
    C   = lambda s: Fore.CYAN    + Style.BRIGHT + str(s) + Style.RESET_ALL
    M   = lambda s: Fore.MAGENTA + Style.BRIGHT + str(s) + Style.RESET_ALL
    W   = lambda s: Fore.WHITE   + str(s) + Style.RESET_ALL
    DIM = lambda s: Style.DIM    + str(s) + Style.RESET_ALL
except ImportError:
    sys.exit("Missing dependency -- run:  pip install colorama")

PATCHER_VERSION = "1.2"

# ---- project paths -----------------------------------------------------------
ROOT           = Path(__file__).resolve().parents[1]
JNI_DIR        = ROOT / "app/src/main/jniLibs/arm64-v8a"
LOADER_POLICY  = ROOT / "native/loader-policy.c"
LOADER_ADAPTER = ROOT / "native/loader-adapter.c"
SPAWN_GUARD    = ROOT / "app/src/main/assets/engine/spawn-guard.sh"
LOADER_STATE   = ROOT / "app/src/main/java/dev/rootcatch/nativeengine/LoaderState.java"
BUILD_GRADLE   = ROOT / "app/build.gradle.kts"
CONN_STRATEGY  = ROOT / "app/src/main/java/dev/rootcatch/nativeengine/ConnectionStrategy.java"
APK_OUT        = ROOT / "app/build/outputs/apk/release/app-release.apk"
PATCH_LIBS     = ["librootcatch-inject.so", "libsilph-spawn.so"]

# ---- slot width -- must equal len("frida-agent-64.so") == 17 ----------------
BINARY_SLOT = 17

# ---- randomisation pools -----------------------------------------------------
# memfd names -- all <= 17 chars (= len("frida-agent-64.so")); shorter = null-padded
MEMFD_POOL = [
    "art-jit-zygote.so",
    "art-boot-image.so",
    "system-runtime.so",
    "libjni-runtime.so",
    "art-jit-object.so",
    "libart-profile.so",
    "dalvik-jit-opt.so",
    "art-jit-cache.so",
    "art-dex-cache.so",
    "libc-dl-maps.so",
]

# shell namespace variable in ConnectionStrategy.java command templates
NSVAR_POOL = [
    "silph_parent_ns",
    "mnt_ns_ref",
    "parent_mnt_ns",
    "proc_ns_ref",
    "mnt_scope_ref",
    "ns_handle",
    "root_ns_ref",
    "zygote_ns_fd",
]

# SILPH_LOADER_SCOPE env var name -- must match in loader-adapter.c + spawn-guard.sh
SCOPE_VAR_POOL = [
    "SILPH_LOADER_SCOPE",
    "ANDROID_PROC_SCOPE",
    "SYSTEM_LOADER_CTX",
    "PROC_RUNTIME_SCOPE",
    "ART_LOADER_SCOPE",
    "DALVIK_RUNTIME_SCOPE",
    "RUNTIME_LOADER_CTX",
    "SYSTEM_RUNTIME_SCOPE",
    "PROC_LOADER_CTX",
    "ART_RUNTIME_SCOPE",
]

# SILPH_LOADER_CONTEXT_FD env var name -- must match in loader-adapter.c + spawn-guard.sh
CTXFD_VAR_POOL = [
    "SILPH_LOADER_CONTEXT_FD",
    "ANDROID_CONTEXT_FD",
    "SYSTEM_CONTEXT_FD",
    "ART_CONTEXT_FD",
    "PROC_CONTEXT_FD",
    "RUNTIME_CONTEXT_FD",
    "DALVIK_CONTEXT_FD",
    "LOADER_SELINUX_FD",
    "PROC_SELINUX_FD",
    "ART_SELINUX_FD",
]

# adapter tag -- must match in loader-adapter.c + LoaderState.java
ADAPTER_TAG_POOL = [
    "readonly-apk-v3",
    "readonly-apk-v4",
    "apk-memfd-v2",
    "apk-loader-v3",
    "memfd-readonly-v1",
    "apk-readonly-v2",
    "loader-apk-v3",
    "apk-context-v2",
    "readonly-lib-v3",
    "memfd-apk-v2",
]

# application ID in build.gradle.kts
APP_ID_POOL = [
    "dev.rootcatch.ugphonetest",
    "com.android.systemservice",
    "com.system.runtime.helper",
    "com.android.process.monitor",
    "com.device.manager.service",
    "com.runtime.system.core",
    "com.android.overlay.service",
    "com.system.audio.manager",
    "com.android.media.service",
    "com.google.android.sync",
]

# ---- display helpers ---------------------------------------------------------
def banner():
    print(G(
        "\n"
        "  _  __         _                     __  __           _\n"
        " | |/ /___  ___| | ___  ___  _ __    |  \\/  | ___   __| |\n"
        " | ' // _ \\/ __| |/ _ \\/ _ \\| '_ \\  | |\\/| |/ _ \\ / _` |\n"
        " | . \\  __/ (__| |  __/ (_) | | | |  | |  | | (_) | (_| |\n"
        " |_|\\_\\___|\\___|\\_|\\___||___/|_| |_|  |_|  |_|\\___/ \\__,_|"
    ))
    print(C("  KecleonMod  -  Random-Patch & Build") +
          DIM("  (Patcher v" + PATCHER_VERSION + ")"))
    print(DIM("  " + "-" * 52) + "\n")

def section(title):
    print(Y("\n  >>  " + title))

def ok(msg, note=""):
    suffix = ("  " + DIM(note)) if note else ""
    print(G("  [OK]  " + msg) + suffix)

def row(label, value, colour=W):
    print("     " + C("{:<26}".format(label)) + colour(value))

def warn(msg):
    print(Y("  [!]  " + msg))

def fail(msg):
    print(R("\n  [X]  " + msg + "\n"))
    sys.exit(1)

def dry_tag(dry):
    return DIM("  [dry-run - no changes written]") if dry else ""

# ---- core utilities ----------------------------------------------------------
def sha256_file(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(65536), b""):
            h.update(chunk)
    return h.hexdigest()

def patch_binary_slot(data, old_str, new_str, slot):
    old_b = old_str.encode().ljust(slot, b"\x00")
    new_b = new_str.encode().ljust(slot, b"\x00")
    if len(old_b) != slot or len(new_b) != slot:
        raise ValueError("Name exceeds slot width {}: '{}' or '{}'".format(slot, old_str, new_str))
    result = bytearray(data)
    pos = 0
    count = 0
    while True:
        idx = data.find(old_b, pos)
        if idx == -1:
            break
        result[idx:idx + slot] = new_b
        count += 1
        pos = idx + slot
    return bytes(result), count

def simple_replace(path, old, new, dry, label=None):
    """Replace all occurrences of old->new in a text file. Returns count."""
    text = path.read_text(encoding="utf-8")
    count = text.count(old)
    if count == 0:
        return 0
    if not dry:
        # newline='\n' prevents Python from converting LF->CRLF on Windows.
        # spawn-guard.sh runs on Android (mksh/ash) which requires LF-only endings.
        path.write_text(text.replace(old, new), encoding="utf-8", newline="\n")
    return count

# ---- readers -----------------------------------------------------------------
def read_memfd_name():
    text = LOADER_POLICY.read_text(encoding="utf-8")
    m = re.search(r'"/memfd:([^"]+) \(deleted\)"', text)
    if not m:
        fail("memfd name not found in " + str(LOADER_POLICY.relative_to(ROOT)))
    return m.group(1)

def read_nsvar_name():
    text = CONN_STRATEGY.read_text(encoding="utf-8")
    m = re.search(r'(\w+)=\$\(readlink /proc/self/ns/mnt\)', text)
    return m.group(1) if m else "silph_parent_ns"

def read_scope_var():
    text = LOADER_ADAPTER.read_text(encoding="utf-8")
    m = re.search(r'const char \*scope = getenv\("([^"]+)"\)', text)
    return m.group(1) if m else "SILPH_LOADER_SCOPE"

def read_ctxfd_var():
    text = LOADER_ADAPTER.read_text(encoding="utf-8")
    m = re.search(r'const char \*descriptor = getenv\("([^"]+)"\)', text)
    return m.group(1) if m else "SILPH_LOADER_CONTEXT_FD"

def read_adapter_tag():
    text = LOADER_STATE.read_text(encoding="utf-8")
    m = re.search(r'ADAPTER = "([^"]+)"', text)
    return m.group(1) if m else "readonly-apk-v3"

def read_app_id():
    text = BUILD_GRADLE.read_text(encoding="utf-8")
    m = re.search(r'applicationId = "([^"]+)"', text)
    return m.group(1) if m else "dev.rootcatch.ugphonetest"

def pick_other(pool, current):
    choices = [x for x in pool if x != current]
    if not choices:
        fail("Randomisation pool exhausted -- all names match current")
    return random.choice(choices)

# ---- patch steps -------------------------------------------------------------
def step_patch_binaries(cur, new, dry):
    section("Patching native .so binaries" + dry_tag(dry))
    ORIG_FRIDA = "frida-agent-64.so"
    hashes = {}

    for lib in PATCH_LIBS:
        path = JNI_DIR / lib
        if not path.exists():
            fail("Missing: " + str(path) + "\n     Run: python tools/prepare_native.py")

        data = path.read_bytes()
        patched, count = patch_binary_slot(data, cur, new, BINARY_SLOT)

        if count == 0:
            patched, count = patch_binary_slot(data, ORIG_FRIDA, new, BINARY_SLOT)
            if count:
                warn(lib + ": found original Frida name (binary was never custom-patched)")

        if count:
            if not dry:
                path.write_bytes(patched)
            h = hashlib.sha256(patched).hexdigest()
            hashes[lib] = sha256_file(path) if not dry else h
            ok(lib, "{} patch{}".format(count, "es" if count != 1 else ""))
            row("occurrences patched", str(count), G)
            row("new SHA-256", h[:40] + "...", W)
        else:
            warn(lib + ": '" + cur + "' not found -- binary unchanged")
            hashes[lib] = sha256_file(path)
            row("SHA-256 (unchanged)", sha256_file(path)[:40] + "...", DIM)

    return hashes

def step_patch_loader_policy(cur, new, dry):
    section("Patching native/loader-policy.c" + dry_tag(dry))
    text = LOADER_POLICY.read_text(encoding="utf-8")
    updated = (text
               .replace("/memfd:" + cur + " (deleted)", "/memfd:" + new + " (deleted)")
               .replace("memfd:" + cur + " (deleted)",  "memfd:" + new + " (deleted)"))
    if updated == text:
        warn("loader-policy.c: no matching string found")
    else:
        if not dry:
            LOADER_POLICY.write_text(updated, encoding="utf-8", newline="\n")
        ok("native/loader-policy.c")
        row("old memfd name", "'" + cur + "'", Y)
        row("new memfd name", "'" + new + "'", G)

def step_patch_ns_var(cur, new, dry):
    section("Patching spawn namespace variable" + dry_tag(dry))
    count = simple_replace(CONN_STRATEGY, cur, new, dry)
    if count == 0:
        warn("ConnectionStrategy.java: variable '" + cur + "' not found")
    else:
        if not dry:
            pass  # already written by simple_replace
        ok("ConnectionStrategy.java")
        row("old variable", "'" + cur + "'", Y)
        row("new variable", "'" + new + "'", G)
        row("occurrences replaced", str(count), G)

def step_patch_env_vars(cur_scope, new_scope, cur_ctxfd, new_ctxfd, dry):
    section("Patching loader env var names" + dry_tag(dry))

    # loader-adapter.c -- scope var (getenv + 2x unsetenv occurrences)
    c1 = simple_replace(LOADER_ADAPTER, cur_scope, new_scope, dry)
    # loader-adapter.c -- context fd var
    c2 = simple_replace(LOADER_ADAPTER, cur_ctxfd, new_ctxfd, dry)

    # spawn-guard.sh -- scope var
    c3 = simple_replace(SPAWN_GUARD, cur_scope, new_scope, dry)
    # spawn-guard.sh -- context fd var
    c4 = simple_replace(SPAWN_GUARD, cur_ctxfd, new_ctxfd, dry)

    if c1 + c3 == 0:
        warn("Scope var '" + cur_scope + "' not found in either file")
    else:
        ok("SCOPE var: " + cur_scope + " -> " + new_scope)
        row("loader-adapter.c hits", str(c1), G)
        row("spawn-guard.sh hits",   str(c3), G)

    if c2 + c4 == 0:
        warn("ContextFD var '" + cur_ctxfd + "' not found in either file")
    else:
        ok("CTXFD var: " + cur_ctxfd + " -> " + new_ctxfd)
        row("loader-adapter.c hits", str(c2), G)
        row("spawn-guard.sh hits",   str(c4), G)

def step_patch_adapter_tag(cur, new, dry):
    section("Patching loader adapter tag" + dry_tag(dry))

    c1 = simple_replace(LOADER_ADAPTER, cur, new, dry)
    c2 = simple_replace(LOADER_STATE,   cur, new, dry)

    if c1 + c2 == 0:
        warn("Adapter tag '" + cur + "' not found")
    else:
        ok("adapter tag: '" + cur + "'  ->  '" + new + "'")
        row("loader-adapter.c hits", str(c1), G)
        row("LoaderState.java hits",  str(c2), G)

def step_patch_app_id(cur, new, dry):
    section("Patching application ID" + dry_tag(dry))
    count = simple_replace(BUILD_GRADLE, cur, new, dry)
    if count == 0:
        warn("applicationId '" + cur + "' not found in build.gradle.kts")
    else:
        ok("build.gradle.kts")
        row("old application ID", cur, Y)
        row("new application ID", new, G)
        if not dry:
            warn("New app ID requires uninstall of previous build before installing")

def step_update_gradle_hashes(hashes, dry):
    section("Updating integrity hashes in build.gradle.kts" + dry_tag(dry))
    text = BUILD_GRADLE.read_text(encoding="utf-8")
    for lib, new_hash in hashes.items():
        pattern = r'("' + re.escape(lib) + r'"\s+to\s+")([0-9a-f]{64})'
        m = re.search(pattern, text)
        if not m:
            warn("Hash entry for '" + lib + "' not found in build.gradle.kts")
            continue
        old_hash = m.group(2)
        if old_hash == new_hash:
            row(lib[:28], "hash unchanged", DIM)
            continue
        text = re.sub(pattern, r'\g<1>' + new_hash, text)
        ok(lib)
        row("old SHA-256", old_hash[:32] + "...", Y)
        row("new SHA-256", new_hash[:32] + "...", G)
    if not dry:
        BUILD_GRADLE.write_text(text, encoding="utf-8", newline="\n")

def step_update_strategy_hashes(hashes, dry):
    section("Updating runtime hashes in ConnectionStrategy.java" + dry_tag(dry))
    text = CONN_STRATEGY.read_text(encoding="utf-8")
    updated = text
    for lib, new_hash in hashes.items():
        pattern = r'"' + re.escape(lib) + r'"[^;]+?"([0-9a-f]{64})"'
        m = re.search(pattern, updated, re.DOTALL)
        if not m:
            warn("Enum hash for '" + lib + "' not found in ConnectionStrategy.java")
            continue
        old_hash = m.group(1)
        if old_hash == new_hash:
            row(lib[:28], "hash unchanged", DIM)
            continue
        count = updated.count(old_hash)
        updated = updated.replace(old_hash, new_hash)
        ok(lib)
        row("old SHA-256", old_hash[:32] + "...", Y)
        row("new SHA-256", new_hash[:32] + "...", G)
        row("occurrences updated", str(count), G)
    if updated != text and not dry:
        CONN_STRATEGY.write_text(updated, encoding="utf-8", newline="\n")

# ---- engine build ------------------------------------------------------------
def step_build_engine():
    section("Building engine (JS agent)")
    build_script = ROOT / "engine/build.cjs"
    if not build_script.exists():
        warn("engine/build.cjs not found -- skipping engine pre-build")
        return
    proc = subprocess.run(
        ["node", str(build_script)],
        cwd=str(ROOT / "engine"),
        capture_output=True,
        text=True, encoding="utf-8", errors="replace",
    )
    if proc.returncode == 0:
        ok("engine/build.cjs")
        for line in (proc.stdout or "").strip().splitlines():
            print(DIM("       " + line))
    else:
        warn("engine build errors (Gradle will retry):")
        for line in (proc.stderr or proc.stdout or "").strip().splitlines()[:10]:
            print(R("       " + line))

# ---- gradle build ------------------------------------------------------------
def step_build_apk():
    section("Building release APK")
    gradlew = ROOT / ("gradlew.bat" if sys.platform == "win32" else "gradlew")
    if not gradlew.exists():
        fail("gradlew not found at " + str(gradlew))

    print(M("\n  +-- " + gradlew.name + " assembleRelease"))
    print(DIM("  +" + "-" * 54) + "\n")

    proc = subprocess.Popen(
        [str(gradlew), "assembleRelease"],
        cwd=str(ROOT),
        stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
        text=True, encoding="utf-8", errors="replace", bufsize=1,
    )
    for raw in proc.stdout:
        line = raw.rstrip()
        if not line:
            continue
        if line.startswith("> Task"):
            colour = C
        elif "BUILD SUCCESSFUL" in line:
            colour = G
        elif "BUILD FAILED" in line or ": error:" in line or "error:" in line:
            colour = R
        elif "warning:" in line.lower():
            colour = Y
        elif any(k in line for k in ("LOADER_SHA256", "libsilph", "librootcatch", "loader")):
            colour = M
        elif line.startswith("w:") or line.startswith("e:"):
            colour = Y
        else:
            colour = DIM
        print("  " + colour(line))

    proc.wait()
    return proc.returncode

# ---- main --------------------------------------------------------------------
def main():
    ap = argparse.ArgumentParser(
        description=__doc__,
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    ap.add_argument("--dry", action="store_true",
                    help="Show what would change without writing files or building")
    ap.add_argument("--no-app-id", action="store_true",
                    help="Skip randomising the application ID")
    args = ap.parse_args()

    banner()

    # 1. Read current state
    section("Reading current configuration")
    cur_memfd   = read_memfd_name()
    cur_nsvar   = read_nsvar_name()
    cur_scope   = read_scope_var()
    cur_ctxfd   = read_ctxfd_var()
    cur_adapter = read_adapter_tag()
    cur_app_id  = read_app_id()

    row("memfd name    ", cur_memfd,   Y)
    row("ns variable   ", cur_nsvar,   Y)
    row("scope env var ", cur_scope,   Y)
    row("ctxfd env var ", cur_ctxfd,   Y)
    row("adapter tag   ", cur_adapter, Y)
    row("application ID", cur_app_id,  Y)

    new_memfd   = pick_other(MEMFD_POOL,       cur_memfd)
    new_nsvar   = pick_other(NSVAR_POOL,        cur_nsvar)
    new_scope   = pick_other(SCOPE_VAR_POOL,    cur_scope)
    new_ctxfd   = pick_other(CTXFD_VAR_POOL,    cur_ctxfd)
    new_adapter = pick_other(ADAPTER_TAG_POOL,  cur_adapter)
    new_app_id  = pick_other(APP_ID_POOL,       cur_app_id)

    print()
    row("-> memfd name    ", new_memfd,   G)
    row("-> ns variable   ", new_nsvar,   G)
    row("-> scope env var ", new_scope,   G)
    row("-> ctxfd env var ", new_ctxfd,   G)
    row("-> adapter tag   ", new_adapter, G)
    row("-> application ID", new_app_id,  G)

    # 2. Apply all patches
    new_hashes = step_patch_binaries(cur_memfd, new_memfd, args.dry)
    step_patch_loader_policy(cur_memfd, new_memfd, args.dry)
    step_patch_ns_var(cur_nsvar, new_nsvar, args.dry)
    step_patch_env_vars(cur_scope, new_scope, cur_ctxfd, new_ctxfd, args.dry)
    step_patch_adapter_tag(cur_adapter, new_adapter, args.dry)
    if not args.no_app_id:
        step_patch_app_id(cur_app_id, new_app_id, args.dry)
    else:
        section("Application ID  [skipped by user]")
        row("application ID", cur_app_id + "  (unchanged)", DIM)
    step_update_gradle_hashes(new_hashes, args.dry)
    step_update_strategy_hashes(new_hashes, args.dry)

    # 3. Summary
    section("Patch summary")
    row("memfd string   ", cur_memfd   + "  ->  " + new_memfd,   G)
    row("ns variable    ", cur_nsvar   + "  ->  " + new_nsvar,   G)
    row("scope env var  ", cur_scope   + "  ->  " + new_scope,   G)
    row("ctxfd env var  ", cur_ctxfd   + "  ->  " + new_ctxfd,   G)
    row("adapter tag    ", cur_adapter + "  ->  " + new_adapter, G)
    if not args.no_app_id:
        row("application ID ", cur_app_id  + "  ->  " + new_app_id,  G)
    else:
        row("application ID ", cur_app_id + "  (skipped)", DIM)
    for lib, h in new_hashes.items():
        row("  " + lib[:24], h[:20] + "...", W)
    print()
    row("APK output path", str(APK_OUT), C)

    if args.dry:
        print(Y("\n  [dry-run] No files written, build skipped.\n"))
        return

    # 4. Build engine + APK
    step_build_engine()
    rc = step_build_apk()
    print()

    if rc == 0 and APK_OUT.exists():
        size_mb = APK_OUT.stat().st_size / (1024 * 1024)
        print(G("  +==========================================+"))
        print(G("  |       BUILD SUCCESSFUL  [OK]             |"))
        print(G("  +==========================================+"))
        print()
        print(C("  APK ready:"))
        print(W("  " + str(APK_OUT)))
        print()
        row("size",       "{:.2f} MB".format(size_mb), W)
        row("app ID",     new_app_id,  G)
        row("memfd",      new_memfd,   G)
        row("ns var",     new_nsvar,   G)
        row("scope var",  new_scope,   G)
        row("ctxfd var",  new_ctxfd,   G)
        row("adapter",    new_adapter, G)
        print()
        warn("Different app ID -- uninstall previous build before installing")
    else:
        print(R("  +==========================================+"))
        print(R("  |         BUILD FAILED  [X]                |"))
        print(R("  +==========================================+"))
        print()
        print(DIM("  Check the Gradle output above for the first error."))
        sys.exit(1)


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print(Y("\n\n  [!]  Interrupted by user.\n"))
        sys.exit(1)
    except Exception as exc:
        import traceback
        print(R("\n  [X]  Unexpected error: " + str(exc)))
        print(DIM("  " + "-" * 52))
        traceback.print_exc()
        print(DIM("  " + "-" * 52))
        print(R("  Script aborted. See error above.\n"))
        sys.exit(1)
