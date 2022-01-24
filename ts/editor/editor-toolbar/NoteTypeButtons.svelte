<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "../../lib/ftl";
    import { bridgeCommand } from "../../lib/bridgecommand";
    import { getPlatformString } from "../../lib/shortcuts";

    import ButtonGroup from "../../components/ButtonGroup.svelte";
    import ButtonGroupItem from "../../components/ButtonGroupItem.svelte";
    import LabelButton from "../../components/LabelButton.svelte";
    import Shortcut from "../../components/Shortcut.svelte";

    export let api = {};

    const keyCombination = "Control+L";
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
        <LabelButton
            tooltip="{tr.editingCustomizeCardTemplates()} ({getPlatformString(
                keyCombination,
            )})"
            on:click={() => bridgeCommand("cards")}
        >
            {tr.editingCards()}...
        </LabelButton>
        <Shortcut {keyCombination} on:action={() => bridgeCommand("cards")} />
    </ButtonGroupItem>
</ButtonGroup>
