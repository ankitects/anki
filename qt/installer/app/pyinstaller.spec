# -*- mode: python ; coding: utf-8 -*-

from pathlib import Path

root_dir = Path.cwd().absolute()

a = Analysis(
    [root_dir / 'tools/run.py'],
    pathex=[root_dir / 'out/pylib', root_dir / 'out/qt'],
    binaries=[],
    datas=[(root_dir / "out/qt/_aqt/data", "_aqt/data")],
    hiddenimports=["anki.storage"],
    hookspath=[],
    hooksconfig={},
    runtime_hooks=[],
    excludes=[],
    noarchive=False,
    optimize=0,
)
pyz = PYZ(a.pure)

exe = EXE(
    pyz,
    a.scripts,
    [],
    exclude_binaries=True,
    name='Anki',
    debug=False,
    bootloader_ignore_signals=False,
    strip=False,
    upx=True,
    console=False,
    windowed=True,
    disable_windowed_traceback=False,
    argv_emulation=False,
    target_arch=None,
    codesign_identity=None,
    entitlements_file=None,
)
coll = COLLECT(
    exe,
    a.binaries,
    a.datas,
    strip=False,
    upx=True,
    upx_exclude=[],
    name='anki',
)
app = BUNDLE(coll,
    name='Anki.app',
    icon=None,
    bundle_identifier=None,
)