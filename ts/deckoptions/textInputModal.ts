// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/* eslint
@typescript-eslint/no-non-null-assertion: "off",
 */

import TextInputModal from "./TextInputModal.svelte";

export interface TextInputModalProps {
    title: string;
    prompt: string;
    startingValue?: string;
    onOk: (string) => void;
}

export function textInputModal(props: TextInputModalProps): TextInputModal {
    const target = document.getElementById("modal")!;
    return new TextInputModal({
        target,
        props,
    });
}
