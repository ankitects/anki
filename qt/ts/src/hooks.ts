/* Copyright: Ankitects Pty Ltd and contributors
 * License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html */

function _runHook(hooks: ((...args) => void)[], ...args) {
    for (var i = 0; i < hooks.length; i++) {
        hooks[i](...args);
    }
}

function _runFilter(hooks: ((value: any, ...args) => any)[], value, ...args) {
    for (var i = 0; i < hooks.length; i++) {
        value = hooks[i](value, ...args);
    }
    return value;
}

function _emptyHook(hooks: ((...args) => void)[]) {
    hooks.length = 0;
}

function _addHook(hooks: ((...args) => void)[], hook: (...args) => void) {
    hooks.push(hook);
}

function _singleHook(hooks: ((...args) => void)[], hook: (...args) => void) {
    _emptyHook(hooks);
    _addHook(hooks, hook);
}
