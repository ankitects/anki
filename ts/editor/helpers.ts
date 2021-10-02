// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

// import type { EditingArea } from "./editing-area";

export function getCurrentField(): null {
    return null;
}

export function appendInParentheses(text: string, appendix: string): string {
    return `${text} (${appendix})`;
}

/// trivial wrapper to silence Svelte deprecation warnings
export function execCommand(
    command: string,
    showUI?: boolean | undefined,
    value?: string | undefined
): void {
    document.execCommand(command, showUI, value);
}

/// trivial wrapper to silence Svelte deprecation warnings
export function queryCommandState(command: string): boolean {
    return document.queryCommandState(command);
}
