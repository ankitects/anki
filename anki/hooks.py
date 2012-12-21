# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

"""\
Hooks - hook management and tools for extending Anki
==============================================================================

To find available hooks, grep for runHook and runFilter in the source code.

Instrumenting allows you to modify functions that don't have hooks available.
If you call wrap() with pos='around', the original function will not be called
automatically but can be called with _old().
"""

# Hooks
##############################################################################

_hooks = {}

def runHook(hook, *args):
    "Run all functions on hook."
    hook = _hooks.get(hook, None)
    if hook:
        for func in hook:
            func(*args)

def runFilter(hook, arg, *args):
    hook = _hooks.get(hook, None)
    if hook:
        for func in hook:
            arg = func(arg, *args)
    return arg

def addHook(hook, func):
    "Add a function to hook. Ignore if already on hook."
    if not _hooks.get(hook, None):
        _hooks[hook] = []
    if func not in _hooks[hook]:
        _hooks[hook].append(func)

def remHook(hook, func):
    "Remove a function if is on hook."
    hook = _hooks.get(hook, [])
    if func in hook:
        hook.remove(func)

# Instrumenting
##############################################################################

def wrap(old, new, pos="after"):
    "Override an existing function."
    def repl(*args, **kwargs):
        if pos == "after":
            old(*args, **kwargs)
            return new(*args, **kwargs)
        elif pos == "before":
            new(*args, **kwargs)
            return old(*args, **kwargs)
        else:
            return new(_old=old, *args, **kwargs)
    return repl
