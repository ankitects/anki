# -*- mode: python ; coding: utf-8 -*-

import pkgutil
import os
import sys
from pathlib import Path

# Include all standard library modules
stdlib_path = os.path.dirname(os.__file__)
stdlib_modules = []
for _, modname, _ in pkgutil.walk_packages([stdlib_path]):
    stdlib_modules.append(modname)

root_dir = Path.cwd().absolute()

a = Analysis(
    [root_dir / 'tools/run.py'],
    pathex=[root_dir / 'out/pylib', root_dir / 'out/qt'],
    binaries=[],
    datas=[(root_dir / "out/qt/_aqt/data", "_aqt/data")],
    hiddenimports=[*stdlib_modules, "anki.storage"],
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
