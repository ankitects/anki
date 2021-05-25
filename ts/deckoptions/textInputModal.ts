// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/* eslint
@typescript-eslint/no-non-null-assertion: "off",
 */

import TextInputModal from "./TextInputModal.svelte";

import { checkNightMode } from "lib/nightmode";
import { nightModeKey } from "components/contextKeys";

export interface TextInputModalProps {
    title: string;
    prompt: string;
    startingValue?: string;
    onOk: (text: string) => void;
}

export function textInputModal(props: TextInputModalProps): TextInputModal {
    const target = document.getElementById("modal")!;

    const nightMode = checkNightMode();
    const context = new Map();
    context.set(nightModeKey, nightMode);

    return new TextInputModal({
        target,
        props,
        context,
    } as any);
}
