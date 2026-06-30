# MCAT fork: launcher for the 50k-card engine benchmark (spec §7h) on Windows.
# Sets up the built-backend env, generates the synthetic deck if absent, then
# runs bench.py. Invoked by `just bench`; can also be run directly.
$ErrorActionPreference = 'Stop'
$here = $PSScriptRoot
$root = Split-Path -Parent $here
$env:PYTHONPATH = Join-Path $root 'out/pylib'
$env:ANKI_TEST_MODE = '1'
$py = Join-Path $root 'out/pyenv/scripts/python.exe'
$deck = Join-Path $here 'fixtures/synthetic_50k.anki2'
if (-not (Test-Path $deck)) {
    & $py (Join-Path $here 'make_synthetic_deck.py') --out $deck
}
& $py (Join-Path $here 'bench.py') --deck $deck @args
