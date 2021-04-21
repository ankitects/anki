const modifiers = ["Control", "Alt", "Shift", "Meta"];

const platformModifiers =
    navigator.platform === "MacIntel" ? ["Meta", "Alt", "Shift", "Control"] : modifiers;

function checkKey(event: KeyboardEvent, key: string): boolean {
    return event.code === key;
}

function checkModifiers(event: KeyboardEvent, activeModifiers: string[]): boolean {
    return modifiers.reduce(
        (matches: boolean, modifier: string, currentIndex: number): boolean =>
            matches &&
            event.getModifierState(platformModifiers[currentIndex]) ===
                activeModifiers.includes(modifier),
        true
    );
}

function check(event: KeyboardEvent, modifiersAndKey: string[]): boolean {
    return (
        checkKey(event, modifiersAndKey[modifiersAndKey.length - 1]) &&
        checkModifiers(event, modifiersAndKey.slice(0, -1))
    );
}

function normalizeShortcutString(shortcutString: string): string[][] {
    return shortcutString.split(", ").map((segment) => segment.split("+"));
}

const shortcutTimeoutMs = 350;

function innerShortcut(
    lastEvent: KeyboardEvent,
    callback: (event: KeyboardEvent) => void,
    ...shortcuts: string[][]
): void {
    if (shortcuts.length === 0) {
        callback(lastEvent);
    } else {
        const [nextShortcut, ...restShortcuts] = shortcuts;

        let ivl: number;

        const handler = (event: KeyboardEvent): void => {
            if (check(event, nextShortcut)) {
                innerShortcut(event, callback, ...restShortcuts);
                clearInterval(ivl);
            }
        };

        ivl = setInterval(
            (): void => document.removeEventListener("keydown", handler),
            shortcutTimeoutMs
        );

        document.addEventListener("keydown", handler, { once: true });
    }
}

export function registerShortcut(
    callback: (event: KeyboardEvent) => void,
    shortcutString: string
): () => void {
    const shortcuts = normalizeShortcutString(shortcutString);
    const [firstShortcut, ...restShortcuts] = shortcuts;

    const handler = (event: KeyboardEvent): void => {
        if (check(event, firstShortcut)) {
            innerShortcut(event, callback, ...restShortcuts);
        }
    };

    document.addEventListener("keydown", handler);
    return (): void => document.removeEventListener("keydown", handler);
}
