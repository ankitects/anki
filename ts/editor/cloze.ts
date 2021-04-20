// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
import IconButton from "editor-toolbar/IconButton.svelte";
import type { IconButtonProps } from "editor-toolbar/IconButton";

import { DynamicSvelteComponent, dynamicComponent } from "sveltelib/dynamicComponent";
import * as tr from "anki/i18n";

import bracketsIcon from "./code-brackets.svg";

const clozePattern = /\{\{c(\d+)::/gu;
function getCurrentHighestCloze(increment: boolean): number {
    let highest = 0;

    // @ts-expect-error
    forEditorField([], (field) => {
        const matches = field.editingArea.editable.fieldHTML.matchAll(clozePattern);
        highest = Math.max(
            highest,
            ...[...matches].map((match: RegExpMatchArray): number => Number(match[1]))
        );
    });

    if (increment) {
        highest++;
    }

    return Math.max(1, highest);
}

function onCloze(event: MouseEvent): void {
    const highestCloze = getCurrentHighestCloze(!event.altKey);

    // @ts-expect-error
    wrap(`{{c${highestCloze}::`, "}}");
}

const iconButton = dynamicComponent<typeof IconButton, IconButtonProps>(IconButton);

export function getClozeButton(): DynamicSvelteComponent<typeof IconButton> &
    IconButtonProps {
    return iconButton({
        id: "cloze",
        icon: bracketsIcon,
        onClick: onCloze,
        tooltip: tr.editingClozeDeletionCtrlandshiftandc(),
    });
}
