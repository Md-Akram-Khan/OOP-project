#!/usr/bin/env python3
"""
One-command launcher for the Electricity Billing System.

    python run.py              build if needed, start the server, open the app
    python run.py --build      force a fresh build first
    python run.py --server     run only the backend server (Ctrl+C to stop)
    python run.py --app        open only the desktop app (server must already be running)
    python run.py --check      check Java, .env, build and database, then exit

What it runs:
  * Database  - hosted by Supabase in the cloud, so nothing starts locally. The
                server checks the connection on start-up and explains any problem.
  * Backend   - release/server/ebs-server.jar, the only part that holds the
                database password.
  * Frontend  - release/client/Electricity_Billing_System.jar, the desktop app.

Only the Python standard library is used, so there is nothing to install.
"""

import argparse
import os
import re
import shutil
import signal
import subprocess
import sys
import threading
import time
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent
SRC = ROOT / "src" / "electricity" / "billing" / "system"
LIB = ROOT / "lib"
CLIENT_DIR = ROOT / "release" / "client"
SERVER_DIR = ROOT / "release" / "server"
CLIENT_JAR = CLIENT_DIR / "Electricity_Billing_System.jar"
SERVER_JAR = SERVER_DIR / "ebs-server.jar"
WORK = ROOT / "build" / "release"

GSON = LIB / "gson-2.11.0.jar"
FLATLAF = LIB / "flatlaf-3.7.2.jar"
INTER = LIB / "flatlaf-fonts-inter-4.1.jar"
POSTGRES = LIB / "postgresql-42.7.8.jar"

SHARED = ["common", "model"]
CLIENT_FOLDERS = [".", "client", "ui", "ui/theme", "ui/components", "ui/pages/admin", "ui/pages/customer", "ui/pages/applicant"] + SHARED
SERVER_FOLDERS = ["server", "service", "db", "security", "billing", "meterphoto"] + SHARED
DB_SETTINGS = ("SUPABASE_DB_URL", "SUPABASE_DB_USER", "SUPABASE_DB_PASSWORD")

SERVER_START_TIMEOUT = 60
SECRET = re.compile(r"(password=)[^&\s\"]+", re.IGNORECASE)


# ---- Output ----------------------------------------------------------------

USE_COLOR = sys.stdout.isatty() and os.environ.get("NO_COLOR") is None
if os.name == "nt" and USE_COLOR:
    os.system("")  # turns on ANSI colours in the Windows console


def _can_print(text):
    try:
        text.encode(sys.stdout.encoding or "ascii")
        return True
    except (UnicodeEncodeError, LookupError):
        return False


# Older Windows consoles can't show ✓ or ⚡, so fall back to plain ASCII there.
FANCY = _can_print("✓✗⚡…")
TICK, CROSS, BOLT = ("✓", "✗", "⚡ ") if FANCY else ("+", "x", "")


def paint(text, code):
    return f"\033[{code}m{text}\033[0m" if USE_COLOR else text


def step(msg):
    print(paint("==> ", "36;1") + paint(msg, "1"))


def ok(msg):
    print(paint(f"  {TICK} ", "32;1") + msg)


def warn(msg):
    print(paint("  ! ", "33;1") + msg)


def fail(msg):
    print(paint(f"  {CROSS} ", "31;1") + msg)
    sys.exit(1)


def redact(line):
    line = SECRET.sub(r"\1***", line)
    return line if FANCY else line.encode("ascii", "replace").decode("ascii")


# ---- Settings --------------------------------------------------------------

def read_dotenv():
    """Reads .env the same way the Java code does: the first definition of each key wins."""
    values = {}
    path = ROOT / ".env"
    if not path.is_file():
        return values
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        value = value.strip()
        if len(value) >= 2 and value[0] == value[-1] == '"':
            value = value[1:-1]
        values.setdefault(key.strip(), value)
    return values


def prefer_dotenv(dotenv):
    """
    .env is this project's source of truth for the database. A leftover
    SUPABASE_DB_* environment variable (for example one inherited by an editor
    that was opened before it was removed) would silently override it, so drop
    any that conflict with .env before starting the server.
    """
    dropped = []
    for name in DB_SETTINGS:
        if dotenv.get(name) and os.environ.get(name) and os.environ[name] != dotenv[name]:
            del os.environ[name]
            dropped.append(name)
    return dropped


def setting(name, dotenv, default=None):
    """Environment variables override .env, matching the Java Config class."""
    return os.environ.get(name) or dotenv.get(name) or default


# ---- Checks ----------------------------------------------------------------

def java_version(tool):
    exe = shutil.which(tool)
    if not exe:
        return None
    out = subprocess.run([exe, "-version"], capture_output=True, text=True)
    match = re.search(r'version "?(\d+)', out.stderr + out.stdout)
    return int(match.group(1)) if match else 0


def check_java(need_compiler):
    version = java_version("java")
    if version is None:
        fail("Java isn't installed (or not on PATH). Install JDK 17 or newer from https://adoptium.net")
    if version < 17:
        fail(f"Java {version} found, but Java 17 or newer is required.")
    ok(f"Java {version}")
    if need_compiler:
        if java_version("javac") is None or shutil.which("jar") is None:
            fail("A build is needed, but javac/jar weren't found. Install a full JDK (not just a JRE).")
        ok("Java compiler available")


def check_settings(dotenv, for_server):
    if not (ROOT / ".env").is_file():
        fail("No .env file. Copy .env.example to .env and fill in your Supabase details.")
    ok(".env found")
    if not for_server:
        return
    missing = [n for n in DB_SETTINGS if not setting(n, dotenv)]
    if missing:
        fail("Missing in .env: " + ", ".join(missing))
    overridden = [n for n in DB_SETTINGS if os.environ.get(n) and os.environ.get(n) != dotenv.get(n)]
    if overridden:
        warn("These environment variables override .env: " + ", ".join(overridden))
        warn("If the database can't be reached, remove them (or close and reopen your terminal).")
    url = setting("SUPABASE_DB_URL", dotenv, "")
    host = re.sub(r"^jdbc:postgresql://", "", url).split("/")[0]
    if re.match(r"^db\..*\.supabase\.co", host):
        warn(f"{host} is Supabase's direct address, which only works over IPv6.")
        warn("If the server can't connect, use the Session pooler connection string instead.")
    ok(f"Database: {host or '(not set)'} (Supabase cloud)")


# ---- Build -----------------------------------------------------------------

def newest_source_time():
    return max((p.stat().st_mtime for p in (ROOT / "src").rglob("*.java")), default=0)


def needs_build():
    if not CLIENT_JAR.is_file() or not SERVER_JAR.is_file():
        return True
    built = min(CLIENT_JAR.stat().st_mtime, SERVER_JAR.stat().st_mtime)
    return newest_source_time() > built


def build_jar(name, folders, libs, main_class, out_dir):
    classes = WORK / name
    if classes.exists():
        shutil.rmtree(classes)
    classes.mkdir(parents=True)
    sources = [str(p) for folder in folders for p in sorted((SRC / folder).glob("*.java"))]
    # Pass the file list through an argument file: Windows limits command-line length.
    argfile = WORK / f"{name}.sources"
    argfile.write_text("\n".join(f'"{s}"'.replace("\\", "/") for s in sources), encoding="utf-8")
    classpath = os.pathsep.join(str(lib) for lib in libs)
    result = subprocess.run(
        ["javac", "--release", "17", "-nowarn", "-encoding", "UTF-8", "-d", str(classes), "-cp", classpath, f"@{argfile}"],
        capture_output=True, text=True)
    if result.returncode != 0:
        errors = [l for l in (result.stdout + result.stderr).splitlines() if "InlineMe" not in l]
        print("\n".join(errors[-40:]))
        fail(f"Compiling {name} failed.")

    manifest = WORK / f"{name}.mf"
    manifest.write_text(f"Main-Class: {main_class}\nClass-Path: "
                        + " ".join("lib/" + lib.name for lib in libs) + "\n", encoding="ascii")
    (out_dir / "lib").mkdir(parents=True, exist_ok=True)
    jar = out_dir / f"{name}.jar"
    result = subprocess.run(["jar", "cfm", str(jar), str(manifest), "-C", str(classes), "."], capture_output=True, text=True)
    if result.returncode != 0:
        print(result.stdout + result.stderr)
        fail(f"Packaging {name}.jar failed (is the app or server still running? Close it and try again).")
    for lib in libs:
        shutil.copy2(lib, out_dir / "lib" / lib.name)
    ok(f"Built {jar.relative_to(ROOT)}")


def build():
    for lib in (GSON, FLATLAF, INTER, POSTGRES):
        if not lib.is_file():
            fail(f"Missing library {lib.relative_to(ROOT)}")
    build_jar("Electricity_Billing_System", CLIENT_FOLDERS, [GSON, FLATLAF, INTER],
              "electricity.billing.system.Main", CLIENT_DIR)
    build_jar("ebs-server", SERVER_FOLDERS, [GSON, POSTGRES],
              "electricity.billing.system.server.ApiServer", SERVER_DIR)


# ---- Running ---------------------------------------------------------------

def server_healthy(base_url):
    try:
        with urllib.request.urlopen(base_url + "/api/health", timeout=2) as response:
            return response.status == 200
    except (urllib.error.URLError, OSError):
        return False


def database_reachable(base_url):
    """Asks the server a question that needs the database (the health check alone doesn't touch it)."""
    try:
        with urllib.request.urlopen(base_url + "/api/auth/admin-exists", timeout=30) as response:
            return response.status == 200
    except (urllib.error.URLError, OSError):
        return False


def stream(process, label, color, lines):
    """Copies a child's output to the console with a coloured prefix, hiding any passwords."""
    for raw in process.stdout:
        line = redact(raw.rstrip())
        lines.append(line)
        print(paint(f"[{label}] ", color) + line, flush=True)


def start_server(port, base_url):
    if server_healthy(base_url):
        ok(f"A server is already running at {base_url}; using it.")
        return None
    step(f"Starting the backend server on port {port}")
    env = dict(os.environ, EBS_SERVER_PORT=str(port))
    process = subprocess.Popen(["java", "-jar", SERVER_JAR.name], cwd=SERVER_DIR, env=env,
                               stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, bufsize=1)
    lines = []
    threading.Thread(target=stream, args=(process, "server", "35", lines), daemon=True).start()
    deadline = time.time() + SERVER_START_TIMEOUT
    while time.time() < deadline:
        if process.poll() is not None:
            time.sleep(0.3)  # let the last log lines arrive
            fail("The server stopped during start-up. Its log above explains why.")
        if server_healthy(base_url):
            ok(f"Backend ready at {base_url}")
            return process
        time.sleep(0.5)
    stop(process)
    fail(f"The server didn't become ready within {SERVER_START_TIMEOUT} seconds.")


def start_app(base_url):
    step("Opening the desktop app")
    env = dict(os.environ, EBS_API_URL=base_url)
    # The app never needs the database password; don't hand it over.
    for name in DB_SETTINGS:
        env.pop(name, None)
    process = subprocess.Popen(["java", "-jar", CLIENT_JAR.name], cwd=CLIENT_DIR, env=env,
                               stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, bufsize=1)
    threading.Thread(target=stream, args=(process, "app", "34", []), daemon=True).start()
    ok("App window opening. Close it to finish.")
    return process


def stop(process):
    if process is None or process.poll() is not None:
        return
    process.terminate()
    try:
        process.wait(timeout=8)
    except subprocess.TimeoutExpired:
        process.kill()


def main():
    parser = argparse.ArgumentParser(description="Run the Electricity Billing System (database, backend and app).")
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--server", action="store_true", help="run only the backend server")
    mode.add_argument("--app", action="store_true", help="open only the desktop app")
    mode.add_argument("--check", action="store_true", help="run the checks and exit")
    parser.add_argument("--build", action="store_true", help="rebuild the JARs before running")
    args = parser.parse_args()

    os.chdir(ROOT)
    # Show each line straight away, even when output goes to an IDE panel or a file.
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(line_buffering=True)
    print(paint(f"\n  {BOLT}Electricity Billing System\n", "36;1"))
    dotenv = read_dotenv()
    ignored = prefer_dotenv(dotenv)
    port = int(setting("EBS_SERVER_PORT", dotenv, "8080"))
    base_url = setting("EBS_API_URL", dotenv) if args.app else f"http://localhost:{port}"
    base_url = (base_url or f"http://localhost:{port}").rstrip("/")

    step("Checking your setup")
    if ignored:
        warn("Ignoring old environment variables that disagree with .env: " + ", ".join(ignored))
        warn("Using the .env settings instead. (Restart VS Code or your terminal to clear them for good.)")
    rebuild = args.build or needs_build()
    check_java(need_compiler=rebuild)
    check_settings(dotenv, for_server=not args.app)

    if rebuild:
        step("Building the app and server" + ("" if args.build else " (source code changed)"))
        build()
    else:
        ok("Build is up to date")

    if args.check:
        step("Checking the database connection")
        # The server tests the database as it starts, so a short real start-up is the most honest check.
        server = start_server(port, base_url)
        reachable = database_reachable(base_url)
        stop(server)
        if not reachable:
            fail("The server is running but can't query the database. See its log for the reason.")
        ok("Database query succeeded.")
        step("All checks passed.")
        return

    server = None
    app = None
    try:
        if not args.app:
            server = start_server(port, base_url)
        if args.server:
            if server is None:
                return
            step("Server running. Press Ctrl+C to stop.")
            server.wait()
            return
        if not server_healthy(base_url):
            fail(f"No server is responding at {base_url}. Start it with: python run.py --server")
        app = start_app(base_url)
        app.wait()
        step("App closed.")
    except KeyboardInterrupt:
        print()
        step("Stopping...")
    finally:
        stop(app)
        if server is not None:
            stop(server)
            ok("Backend server stopped.")


if __name__ == "__main__":
    signal.signal(signal.SIGINT, signal.default_int_handler)
    main()
