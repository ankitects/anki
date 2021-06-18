<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import * as tr from "lib/i18n";
    import { disabledKey } from "components/contextKeys";

    import IconButton from "components/IconButton.svelte";
    import WithShortcut from "components/WithShortcut.svelte";
    import WithContext from "components/WithContext.svelte";

    import { bracketsIcon } from "./icons";
    import { forEditorField } from ".";
    import { wrap } from "./wrap";

    const clozePattern = /\{\{c(\d+)::/gu;
    function getCurrentHighestCloze(increment: boolean): number {
        let highest = 0;

        forEditorField([], (field) => {
            const fieldHTML = field.editingArea.editable.fieldHTML;
            const matches: number[] = [];
            let match: RegExpMatchArray | null = null;

            while ((match = clozePattern.exec(fieldHTML))) {
                matches.push(Number(match[1]));
            }

            highest = Math.max(highest, ...matches);
        });

        if (increment) {
            highest++;
        }

        return Math.max(1, highest);
    }

    function onCloze(event: KeyboardEvent | MouseEvent): void {
        const highestCloze = getCurrentHighestCloze(!event.getModifierState("Alt"));
        wrap(`{{c${highestCloze}::`, "}}");
    }
</script>

<WithShortcut shortcut={"Control+Alt?+Shift+C"} let:createShortcut let:shortcutLabel>
    <WithContext key={disabledKey} let:context={disabled}>
        <IconButton
            tooltip={`${tr.editingClozeDeletion()} (${shortcutLabel})`}
            {disabled}
            on:click={onCloze}
            on:mount={createShortcut}
        >
            {@html bracketsIcon}
        </IconButton>
    </WithContext>
</WithShortcut>
