<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import { bridgeCommand } from "lib/bridgecommand";
    import * as tr from "lib/i18n";

    import ButtonGroup from "components/ButtonGroup.svelte";
    import ButtonGroupItem from "components/ButtonGroupItem.svelte";
    import LabelButton from "components/LabelButton.svelte";
    import WithShortcut from "components/WithShortcut.svelte";

    export let api = {};
</script>

<ButtonGroup {api}>
    <ButtonGroupItem>
        <LabelButton
            tooltip={tr.editingCustomizeFields()}
            on:click={() => bridgeCommand("fields")}
        >
            {tr.editingFields()}...
        </LabelButton>
    </ButtonGroupItem>

    <ButtonGroupItem>
        <WithShortcut shortcut={"Control+L"} let:createShortcut let:shortcutLabel>
            <LabelButton
                tooltip={`${tr.editingCustomizeCardTemplates()} (${shortcutLabel})`}
                on:click={() => bridgeCommand("cards")}
                on:mount={(event) => createShortcut(event.detail.button)}
            >
                {tr.editingCards()}...
            </LabelButton>
        </WithShortcut>
    </ButtonGroupItem>
</ButtonGroup>
