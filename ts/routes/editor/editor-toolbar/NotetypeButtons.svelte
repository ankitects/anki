<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "@generated/ftl";
    import { bridgeCommand } from "@tslib/bridgecommand";
    import { getPlatformString } from "@tslib/shortcuts";

    import ButtonGroup from "$lib/components/ButtonGroup.svelte";
    import ButtonGroupItem, {
        createProps,
        setSlotHostContext,
        updatePropsList,
    } from "$lib/components/ButtonGroupItem.svelte";
    import DynamicallySlottable from "$lib/components/DynamicallySlottable.svelte";
    import LabelButton from "$lib/components/LabelButton.svelte";
    import Shortcut from "$lib/components/Shortcut.svelte";

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

        <slot />
    </DynamicallySlottable>
</ButtonGroup>
