// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/**
 * Trivial wrapper to silence Svelte deprecation warnings
 */
export function execCommand(
    command: string,
    showUI?: boolean | undefined,
    value?: string | undefined,
): void {
    document.execCommand(command, showUI, value);
}

/**
 * Trivial wrappers to silence Svelte deprecation warnings
 */
export function queryCommandState(command: string): boolean {
    return document.queryCommandState(command);
}
