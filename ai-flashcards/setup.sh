#!/usr/bin/env bash
# FlashAI – setup script (Linux / macOS)
# Creates a .venv, installs deps, and drops a `run` launcher.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
VENV="$SCRIPT_DIR/.venv"

# ── Locate Python 3.9+ ───────────────────────────────────────────────────────
find_python() {
  for candidate in python3 python3.13 python3.12 python3.11 python3.10 python3.9; do
    if command -v "$candidate" &>/dev/null; then
      ver=$("$candidate" -c 'import sys; print(sys.version_info[:2])')
      # crude check: must be >= (3, 9)
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

# ── Tkinter check ────────────────────────────────────────────────────────────
if ! "$PYTHON" -c 'import tkinter' 2>/dev/null; then
  echo ""
  echo "⚠️  tkinter is not available for this Python installation."
  echo "    On Debian/Ubuntu:  sudo apt install python3-tk"
  echo "    On Fedora:         sudo dnf install python3-tkinter"
  echo "    On macOS (brew):   brew install python-tk"
  echo ""
  read -rp "Continue anyway? [y/N] " ans
  [[ "$ans" =~ ^[Yy]$ ]] || exit 1
fi

# ── Create / reuse virtual environment ──────────────────────────────────────
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

echo "📦  Installing dependencies …"
"$PIP" install --quiet -r "$SCRIPT_DIR/requirements.txt"

echo ""
echo "──────────────────────────────────────────────"
echo "✅  Setup complete!"
echo ""
echo "  Run the desktop app:"
echo "    $SCRIPT_DIR/run.sh"
echo ""
echo "  Or activate the venv first:"
echo "    source $VENV/bin/activate"
echo "    python flashai.py"
echo "──────────────────────────────────────────────"

# ── Write a convenience run script ──────────────────────────────────────────
cat > "$SCRIPT_DIR/run.sh" <<'RUNSCRIPT'
#!/usr/bin/env bash
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
exec "$SCRIPT_DIR/.venv/bin/python" "$SCRIPT_DIR/flashai.py" "$@"
RUNSCRIPT
chmod +x "$SCRIPT_DIR/run.sh"
echo "📝  run.sh created"
