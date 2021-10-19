// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { registerShortcut } from "../lib/shortcuts";

export function shortcut(
    _node: Node,
    {
        action,
        keyCombination,
        target,
    }: {
        action: (event: KeyboardEvent) => void;
        keyCombination: string;
        target?: EventTarget;
    },
): { destroy: () => void } {
    const deregister = registerShortcut(action, keyCombination, target ?? document);

    return {
        destroy() {
            deregister();
        },
    };
}

export default shortcut;
