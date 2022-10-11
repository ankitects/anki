<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import ButtonGroup from "../../components/ButtonGroup.svelte";
    import ButtonGroupItem, {
        createProps,
        setSlotHostContext,
        updatePropsList,
    } from "../../components/ButtonGroupItem.svelte";
    import DynamicallySlottable from "../../components/DynamicallySlottable.svelte";
    import Button from "../../components/IconButton.svelte";
    import Shortcut from "../../components/Shortcut.svelte";
    import { bridgeCommand } from "../../lib/bridgecommand";
    import * as tr from "../../lib/ftl";
    import { getPlatformString } from "../../lib/shortcuts";

    export let api = {};

    const keyCombination = "Control+L";
</script>

<ButtonGroup>
    <DynamicallySlottable
        slotHost={ButtonGroupItem}
        {createProps}
        {updatePropsList}
        {setSlotHostContext}
        {api}
    >
        <ButtonGroupItem>
            <Button
                tooltip={tr.editingCustomizeFields()}
                on:click={() => bridgeCommand("fields")}
            >
                {tr.editingFields()}...
            </Button>
        </ButtonGroupItem>

        <ButtonGroupItem>
            <Button
                tooltip="{tr.editingCustomizeCardTemplates()} ({getPlatformString(
                    keyCombination,
                )})"
                on:click={() => bridgeCommand("cards")}
            >
                {tr.editingCards()}...
            </Button>
            <Shortcut {keyCombination} on:action={() => bridgeCommand("cards")} />
        </ButtonGroupItem>

        <slot />
    </DynamicallySlottable>
</ButtonGroup>
