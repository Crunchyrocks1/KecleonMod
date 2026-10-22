#!/usr/bin/env python3
"""
KecleonMod -- Random-Patch & Build
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

# ---- project paths -----------------------------------------------------------
ROOT          = Path(__file__).resolve().parents[1]
JNI_DIR       = ROOT / "app/src/main/jniLibs/arm64-v8a"
LOADER_POLICY = ROOT / "native/loader-policy.c"
BUILD_GRADLE  = ROOT / "app/build.gradle.kts"
CONN_STRATEGY = ROOT / "app/src/main/java/dev/rootcatch/nativeengine/ConnectionStrategy.java"
APK_OUT       = ROOT / "app/build/outputs/apk/release/app-release.apk"
PATCH_LIBS    = ["librootcatch-inject.so", "libsilph-spawn.so"]

# ---- slot width -- must equal len("frida-agent-64.so") == 17 ----------------
BINARY_SLOT = 17

# ---- randomisation pools -----------------------------------------------------
# All names must be <= BINARY_SLOT chars; shorter ones get null-padded in the binary
MEMFD_POOL = [
    "art-jit-zygote.so",   # 17 -- current default after initial patch
    "art-boot-image.so",   # 17
    "system-runtime.so",   # 17
    "libjni-runtime.so",   # 17
    "art-jit-object.so",   # 17
    "libart-profile.so",   # 17
    "dalvik-jit-opt.so",   # 17
    "art-jit-cache.so",    # 16 (+ 1 null pad)
    "art-dex-cache.so",    # 16 (+ 1 null pad)
    "libc-dl-maps.so",     # 15 (+ 2 null pads)
]

# Shell namespace variable name embedded in ConnectionStrategy.java string templates
NSVAR_POOL = [
    "silph_parent_ns",   # current default
    "mnt_ns_ref",
    "parent_mnt_ns",
    "proc_ns_ref",
    "mnt_scope_ref",
    "ns_handle",
    "root_ns_ref",
    "zygote_ns_fd",
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
    print(C("  KecleonMod  -  Random-Patch & Build Tool"))
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
    """
    Find all occurrences of old_str (null-padded to `slot` bytes) in data
    and replace with new_str (also null-padded to `slot` bytes).
    Returns (patched_bytes, match_count).
    """
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
                warn(lib + ": found original Frida name '" + ORIG_FRIDA + "' (binary was never custom-patched)")

        if count:
            if not dry:
                path.write_bytes(patched)
            h = hashlib.sha256(patched).hexdigest()
            hashes[lib] = sha256_file(path) if not dry else hashlib.sha256(patched).hexdigest()
            ok(lib, "{} patch{}".format(count, "es" if count != 1 else ""))
            row("occurrences patched", str(count), G)
            row("new SHA-256", h[:40] + "...", W)
        else:
            warn(lib + ": string '" + cur + "' not found -- binary unchanged")
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
            LOADER_POLICY.write_text(updated, encoding="utf-8")
        ok("native/loader-policy.c")
        row("old memfd name", "'" + cur + "'", Y)
        row("new memfd name", "'" + new + "'", G)

def step_patch_ns_var(cur, new, dry):
    section("Patching shell namespace variable in ConnectionStrategy.java" + dry_tag(dry))
    text = CONN_STRATEGY.read_text(encoding="utf-8")
    updated = text.replace(cur, new)
    if updated == text:
        warn("ConnectionStrategy.java: variable '" + cur + "' not found")
    else:
        count = text.count(cur)
        if not dry:
            CONN_STRATEGY.write_text(updated, encoding="utf-8")
        ok("ConnectionStrategy.java")
        row("old variable", "'" + cur + "'", Y)
        row("new variable", "'" + new + "'", G)
        row("occurrences replaced", str(count), G)

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
        BUILD_GRADLE.write_text(text, encoding="utf-8")

def step_update_strategy_hashes(hashes, dry):
    """Update the SHA-256 values inside the ConnectionStrategy enum (runtime integrity check)."""
    section("Updating runtime hashes in ConnectionStrategy.java" + dry_tag(dry))
    text = CONN_STRATEGY.read_text(encoding="utf-8")
    updated = text
    for lib, new_hash in hashes.items():
        # Find the current hash that is paired with this library name in the enum
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
        CONN_STRATEGY.write_text(updated, encoding="utf-8")

# ---- engine build ------------------------------------------------------------
def step_build_engine():
    section("Building engine (JS agent)")
    node_exe = "node"
    build_script = ROOT / "engine/build.cjs"
    if not build_script.exists():
        warn("engine/build.cjs not found -- skipping engine build")
        return
    proc = subprocess.run(
        [node_exe, str(build_script)],
        cwd=str(ROOT / "engine"),
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
    )
    if proc.returncode == 0:
        ok("engine/build.cjs")
        if proc.stdout.strip():
            for line in proc.stdout.strip().splitlines():
                print(DIM("       " + line))
    else:
        warn("engine build reported errors (Gradle will retry):")
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
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        encoding="utf-8",
        errors="replace",
        bufsize=1,
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
    ap.add_argument(
        "--dry",
        action="store_true",
        help="Show what would change without writing files or building",
    )
    args = ap.parse_args()

    banner()

    # 1. Read current state
    section("Reading current configuration")
    cur_memfd = read_memfd_name()
    cur_nsvar = read_nsvar_name()
    row("current memfd name  ", cur_memfd, Y)
    row("current ns variable ", cur_nsvar, Y)

    new_memfd = pick_other(MEMFD_POOL, cur_memfd)
    new_nsvar = pick_other(NSVAR_POOL, cur_nsvar)
    print()
    row("-> new memfd name   ", new_memfd, G)
    row("-> new ns variable  ", new_nsvar, G)

    # 2. Apply patches
    new_hashes = step_patch_binaries(cur_memfd, new_memfd, args.dry)
    step_patch_loader_policy(cur_memfd, new_memfd, args.dry)
    step_patch_ns_var(cur_nsvar, new_nsvar, args.dry)
    step_update_gradle_hashes(new_hashes, args.dry)
    step_update_strategy_hashes(new_hashes, args.dry)

    # 3. Summary
    section("Patch summary")
    row("memfd string", cur_memfd + "  ->  " + new_memfd, G)
    row("ns variable ", cur_nsvar + "  ->  " + new_nsvar, G)
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
        row("size ", "{:.2f} MB".format(size_mb), W)
        row("memfd", new_memfd, G)
        row("ns var", new_nsvar, G)
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
