// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
export function mergeTooltipAndShortcut(
    tooltip: string | undefined,
    shortcutLabel: string | undefined
): string | undefined {
    return tooltip
        ? shortcutLabel
            ? `${tooltip} (${shortcutLabel})`
            : tooltip
        : shortcutLabel
        ? `(${shortcutLabel})`
        : undefined;
}
