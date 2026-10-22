#!/usr/bin/env python3
"""KecleonMod Patcher GUI — double-click patch_build.bat to open."""

import re
import subprocess
import sys
import threading
from pathlib import Path
import tkinter as tk
from tkinter import scrolledtext

ROOT    = Path(__file__).resolve().parent
SCRIPT  = ROOT / "tools/patch_build.py"
APK_OUT = ROOT / "app/build/outputs/apk/release/app-release.apk"
ANSI    = re.compile(r'\x1b\[[0-9;]*m')

BG      = "#1a1a1a"
BG2     = "#111111"
FG      = "#dddddd"
GREEN   = "#00cc66"
BLUE    = "#3399ff"
YELLOW  = "#ffbb33"
RED     = "#ff4444"
DIM     = "#555555"
CYAN    = "#00cccc"


class App:
    def __init__(self):
        self.root = tk.Tk()
        self.root.title("KecleonMod Patcher")
        self.root.geometry("780x540")
        self.root.configure(bg=BG)
        self.root.resizable(True, True)
        self._build()

    def _build(self):
        # ── header ──────────────────────────────────────────────────────────
        hdr = tk.Frame(self.root, bg=BG)
        hdr.pack(fill=tk.X, padx=14, pady=(12, 2))

        tk.Label(hdr, text="KecleonMod Patcher",
                 fg=GREEN, bg=BG,
                 font=("Consolas", 15, "bold")).pack(side=tk.LEFT)

        tk.Label(hdr, text="Patcher v1.2",
                 fg=GREEN, bg=BG,
                 font=("Consolas", 10, "bold")).pack(side=tk.LEFT, padx=(10, 0), pady=(4, 0))

        tk.Label(hdr, text="| APK v1.9.3",
                 fg=DIM, bg=BG,
                 font=("Consolas", 10)).pack(side=tk.LEFT, padx=(6, 0), pady=(4, 0))

        # ── log ─────────────────────────────────────────────────────────────
        self.log = scrolledtext.ScrolledText(
            self.root, bg=BG2, fg=FG,
            font=("Consolas", 9),
            state=tk.DISABLED, wrap=tk.WORD,
            relief=tk.FLAT, borderwidth=0,
            padx=8, pady=6,
        )
        self.log.pack(fill=tk.BOTH, expand=True, padx=10, pady=(6, 4))

        self.log.tag_config("ok",      foreground=GREEN)
        self.log.tag_config("err",     foreground=RED)
        self.log.tag_config("warn",    foreground=YELLOW)
        self.log.tag_config("section", foreground=CYAN,  font=("Consolas", 9, "bold"))
        self.log.tag_config("apk",     foreground=GREEN, font=("Consolas", 9, "bold"))
        self.log.tag_config("dim",     foreground=DIM)
        self.log.tag_config("done",    foreground=GREEN, font=("Consolas", 9, "bold"))

        # ── buttons ──────────────────────────────────────────────────────────
        btn_row = tk.Frame(self.root, bg=BG)
        btn_row.pack(pady=(2, 8))

        self.patch_btn = tk.Button(
            btn_row, text="  Patch & Build  ",
            command=self._run,
            bg=GREEN, fg="#000000",
            font=("Consolas", 11, "bold"),
            relief=tk.FLAT, padx=10, pady=6,
            cursor="hand2", activebackground="#00ff88",
        )
        self.patch_btn.pack(side=tk.LEFT, padx=6)

        self.dry_btn = tk.Button(
            btn_row, text="  Dry Run  ",
            command=lambda: self._run(dry=True),
            bg="#224466", fg=FG,
            font=("Consolas", 10),
            relief=tk.FLAT, padx=10, pady=6,
            cursor="hand2", activebackground="#335577",
        )
        self.dry_btn.pack(side=tk.LEFT, padx=6)

        tk.Button(
            btn_row, text="  Clear  ",
            command=self._clear,
            bg="#2a2a2a", fg=DIM,
            font=("Consolas", 10),
            relief=tk.FLAT, padx=10, pady=6,
            cursor="hand2", activebackground="#333333",
        ).pack(side=tk.LEFT, padx=6)

        # ── options row ──────────────────────────────────────────────────────
        opt_row = tk.Frame(self.root, bg=BG)
        opt_row.pack(pady=(0, 6))

        self.rand_app_id = tk.BooleanVar(value=False)
        cb = tk.Checkbutton(
            opt_row,
            text="Randomise App ID  (requires uninstall of previous build)",
            variable=self.rand_app_id,
            bg=BG, fg=DIM,
            selectcolor="#2a2a2a",
            activebackground=BG, activeforeground=FG,
            font=("Consolas", 9),
            cursor="hand2",
        )
        cb.pack(side=tk.LEFT)

        # ── status bar ───────────────────────────────────────────────────────
        self.status_var = tk.StringVar(value="Ready  --  press Patch & Build to start")
        tk.Label(
            self.root, textvariable=self.status_var,
            fg="#888888", bg="#0d0d0d",
            font=("Consolas", 8), anchor=tk.W, padx=10,
        ).pack(fill=tk.X, side=tk.BOTTOM)

    # ── helpers ───────────────────────────────────────────────────────────────
    def _write(self, text, tag=None):
        self.log.configure(state=tk.NORMAL)
        self.log.insert(tk.END, text, tag or "")
        self.log.see(tk.END)
        self.log.configure(state=tk.DISABLED)

    def _clear(self):
        self.log.configure(state=tk.NORMAL)
        self.log.delete("1.0", tk.END)
        self.log.configure(state=tk.DISABLED)
        self.status_var.set("Ready  --  press Patch & Build to start")

    def _tag(self, line):
        l = line.lower()
        if "[ok]" in l:                          return "ok"
        if "[x]" in l or "failed" in l or "error" in l: return "err"
        if "[!]" in l or "warning" in l:         return "warn"
        if ">>" in line:                          return "section"
        if "apk" in l and (
            "path" in l or "ready" in l or "apk-release" in l.replace("-", "")):
            return "apk"
        if "build successful" in l:               return "done"
        if not line.strip() or line.strip().startswith("-"): return "dim"
        return None

    def _set_buttons(self, enabled):
        s = tk.NORMAL if enabled else tk.DISABLED
        t = "  Patch & Build  " if enabled else "  Running...  "
        self.patch_btn.configure(state=s, text=t,
                                 bg=GREEN if enabled else "#336644")
        self.dry_btn.configure(state=s)

    # ── run worker ────────────────────────────────────────────────────────────
    def _run(self, dry=False):
        self._clear()
        self._set_buttons(False)
        label = "Dry run in progress..." if dry else "Patching & building..."
        self.status_var.set(label)

        def worker():
            cmd = [sys.executable, str(SCRIPT)]
            if dry:
                cmd.append("--dry")
            if not self.rand_app_id.get():
                cmd.append("--no-app-id")
            try:
                proc = subprocess.Popen(
                    cmd, cwd=str(ROOT),
                    stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                    text=True, encoding="utf-8", errors="replace", bufsize=1,
                )
                for raw in proc.stdout:
                    line = ANSI.sub("", raw).rstrip("\n")
                    tag  = self._tag(line)
                    self.root.after(0, self._write, line + "\n", tag)
                proc.wait()
                rc = proc.returncode
            except Exception as exc:
                self.root.after(0, self._write,
                                "\n[X]  Launch error: " + str(exc) + "\n", "err")
                rc = -1

            def finish():
                self._set_buttons(True)
                if rc == 0 and APK_OUT.exists():
                    mb = APK_OUT.stat().st_size / (1024 * 1024)
                    self.status_var.set(
                        "Done   |   " + str(APK_OUT) + "   ({:.1f} MB)".format(mb)
                    )
                    self.root.after(0, self._write,
                        "\n  APK: " + str(APK_OUT) + "\n", "apk")
                elif rc == 0:
                    self.status_var.set("Done (dry run)")
                else:
                    self.status_var.set("Failed  --  exit code " + str(rc))

            self.root.after(0, finish)

        threading.Thread(target=worker, daemon=True).start()

    def run(self):
        self.root.mainloop()


if __name__ == "__main__":
    App().run()
