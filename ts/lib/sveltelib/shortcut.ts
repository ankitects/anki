// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { RegisterShortcutRestParams } from "@tslib/shortcuts";
import { registerShortcut } from "@tslib/shortcuts";

interface ShortcutParams {
    action: (event: KeyboardEvent) => void;
    keyCombination: string;
    params?: RegisterShortcutRestParams;
}

export function shortcut(
    _node: Node,
    { action, keyCombination, params }: ShortcutParams,
): { destroy: () => void } {
    const deregister = registerShortcut(action, keyCombination, params);

    return {
        destroy: deregister,
    };
}

export default shortcut;
