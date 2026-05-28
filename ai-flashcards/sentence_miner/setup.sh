#!/usr/bin/env bash
# Sentence Miner – setup script (Linux / macOS)
# Creates a .venv, installs deps, and writes run.sh (GUI) and mine.sh (CLI).

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
VENV="$SCRIPT_DIR/.venv"

# ── Locate Python 3.9+ ───────────────────────────────────────────────────────
find_python() {
  for candidate in python3 python3.13 python3.12 python3.11 python3.10 python3.9; do
    if command -v "$candidate" &>/dev/null; then
      if "$candidate" -c 'import sys; sys.exit(0 if sys.version_info >= (3,9) else 1)' 2>/dev/null; then
        echo "$candidate"
        return
      fi
    fi
  done
  echo ""
}

PYTHON=$(find_python)
if [[ -z "$PYTHON" ]]; then
  echo "❌  Python 3.9 or later not found. Please install it first."
  echo "    https://www.python.org/downloads/"
  exit 1
fi

PY_VER=$("$PYTHON" -c 'import sys; print(".".join(map(str,sys.version_info[:3])))')
echo "✅  Using Python $PY_VER  ($PYTHON)"

# ── Tkinter check (needed for GUI only) ──────────────────────────────────────
if ! "$PYTHON" -c 'import tkinter' 2>/dev/null; then
  echo ""
  echo "⚠️  tkinter is not available — the GUI (run.sh) will not work."
  echo "    On Debian/Ubuntu:  sudo apt install python3-tk"
  echo "    On Fedora:         sudo dnf install python3-tkinter"
  echo "    On macOS (brew):   brew install python-tk"
  echo "    The CLI (mine.sh) will still work without tkinter."
  echo ""
  read -rp "Continue anyway? [y/N] " ans
  [[ "$ans" =~ ^[Yy]$ ]] || exit 1
fi

# ── Check external tools ──────────────────────────────────────────────────────
echo ""
for tool in ffmpeg yt-dlp; do
  if command -v "$tool" &>/dev/null; then
    echo "✅  $tool found"
  else
    echo "⚠️  $tool not found in PATH (required at runtime)"
    if [[ "$tool" == "ffmpeg" ]]; then
      echo "    Install: https://ffmpeg.org/download.html"
      echo "             or: sudo apt install ffmpeg / brew install ffmpeg"
    else
      echo "    Install: pip install yt-dlp  or  brew install yt-dlp"
    fi
  fi
done
echo ""

# ── Create / reuse virtual environment ───────────────────────────────────────
if [[ -d "$VENV" ]]; then
  echo "♻️  Reusing existing .venv"
else
  echo "🔧  Creating virtual environment at .venv …"
  "$PYTHON" -m venv "$VENV"
fi

PIP="$VENV/bin/pip"
PYVENV="$VENV/bin/python"

echo "⬆️   Upgrading pip …"
"$PIP" install --quiet --upgrade pip

echo "📦  Installing Python dependencies …"
"$PIP" install --quiet -r "$SCRIPT_DIR/requirements_miner.txt"

# ── Write launcher scripts ────────────────────────────────────────────────────
cat > "$SCRIPT_DIR/run.sh" <<'RUNSCRIPT'
#!/usr/bin/env bash
# Launch the Sentence Miner GUI
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
exec "$SCRIPT_DIR/.venv/bin/python" "$SCRIPT_DIR/gui.py" "$@"
RUNSCRIPT
chmod +x "$SCRIPT_DIR/run.sh"

cat > "$SCRIPT_DIR/mine.sh" <<'MINESCRIPT'
#!/usr/bin/env bash
# Sentence Miner CLI — pass a YouTube URL and options
# Example: ./mine.sh "https://youtu.be/..." --deck Spanish --language es --limit 20
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
exec "$SCRIPT_DIR/.venv/bin/python" "$SCRIPT_DIR/main.py" "$@"
MINESCRIPT
chmod +x "$SCRIPT_DIR/mine.sh"

echo ""
echo "──────────────────────────────────────────────────────"
echo "✅  Setup complete!"
echo ""
echo "  Launch the GUI:"
echo "    $SCRIPT_DIR/run.sh"
echo ""
echo "  Or use the CLI:"
echo '    ./mine.sh "https://youtu.be/VIDEO" --deck Spanish --language es'
echo ""
echo "  Activate the venv manually:"
echo "    source $VENV/bin/activate"
echo "    python gui.py"
echo "──────────────────────────────────────────────────────"
