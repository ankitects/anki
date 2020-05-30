/* Copyright: Ankitects Pty Ltd and contributors
 * License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html */

function _runHook(hooks: ((...args) => void)[], ...args) {
    for (var i = 0; i < hooks.length; i++) {
        hooks[i](...args);
    }
}
