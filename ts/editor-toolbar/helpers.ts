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
