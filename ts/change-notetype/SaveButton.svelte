<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "lib/i18n";
    import type { ChangeNotetypeState } from "./lib";

    import ButtonGroup from "components/ButtonGroup.svelte";
    import ButtonGroupItem from "components/ButtonGroupItem.svelte";

    import LabelButton from "components/LabelButton.svelte";
    import WithShortcut from "components/WithShortcut.svelte";

    export let state: ChangeNotetypeState;

    function save(): void {
        if (document.activeElement instanceof HTMLElement) {
            document.activeElement.blur();
        }
        state.save();
    }
</script>

<ButtonGroup>
    <ButtonGroupItem>
        <WithShortcut shortcut={"Control+Enter"} let:createShortcut let:shortcutLabel>
            <LabelButton
                theme="primary"
                on:click={() => save()}
                tooltip={shortcutLabel}
                on:mount={(event) => createShortcut(event.detail.button)}
                >{tr.actionsSave()}</LabelButton
            >
        </WithShortcut>
    </ButtonGroupItem>
</ButtonGroup>
