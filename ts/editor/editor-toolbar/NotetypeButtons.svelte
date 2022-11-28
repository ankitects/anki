<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { bridgeCommand } from "@tslib/bridgecommand";
    import * as tr from "@tslib/ftl";
    import { getPlatformString } from "@tslib/shortcuts";

    import ButtonGroup from "../../components/ButtonGroup.svelte";
    import ButtonGroupItem, {
        createProps,
        setSlotHostContext,
        updatePropsList,
    } from "../../components/ButtonGroupItem.svelte";
    import DynamicallySlottable from "../../components/DynamicallySlottable.svelte";
    import LabelButton from "../../components/LabelButton.svelte";
    import Shortcut from "../../components/Shortcut.svelte";

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
