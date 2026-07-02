// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
export function mergeTooltipAndShortcut(
    tooltip: string | undefined,
    shortcutLabel: string | undefined,
): string | undefined {
    if (!tooltip && !shortcutLabel) {
        return undefined;
    }

    let buf = tooltip ?? "";
    if (shortcutLabel) {
        buf = `${buf} (${shortcutLabel})`;
    }
    return buf;
}

export const withButton = (f: (button: HTMLButtonElement) => void) => ({ detail }: CustomEvent): void => {
    f(detail.button);
};

export const withSpan = (f: (span: HTMLSpanElement) => void) => ({ detail }: CustomEvent): void => {
    f(detail.span);
};
