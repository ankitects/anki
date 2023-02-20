<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "@tslib/ftl";
    import { noop } from "@tslib/functional";
    import type Modal from "bootstrap/js/dist/modal";
    import { createEventDispatcher, getContext } from "svelte";

    import ButtonToolbar from "../components/ButtonToolbar.svelte";
    import { modalsKey } from "../components/context-keys";
    import Select from "../components/Select.svelte";
    import SelectOption from "../components/SelectOption.svelte";
    import StickyContainer from "../components/StickyContainer.svelte";
    import type { ConfigListEntry, DeckOptionsState } from "./lib";
    import SaveButton from "./SaveButton.svelte";
    import TextInputModal from "./TextInputModal.svelte";

    export let state: DeckOptionsState;
    const configList = state.configList;
    const dispatch = createEventDispatcher();
    const dispatchPresetChange = () => dispatch("presetchange");

    $: value = $configList.findIndex((entry) => entry.current);
    $: label = configLabel($configList[value]);

    function configLabel(entry: ConfigListEntry): string {
        const count = tr.deckConfigUsedByDecks({ decks: entry.useCount });
        return `${entry.name} (${count})`;
    }

    function blur(e: CustomEvent): void {
        state.setCurrentIndex(e.detail.value);
        dispatchPresetChange();
    }

    function onAddConfig(text: string): void {
        const trimmed = text.trim();
        if (trimmed.length) {
            state.addConfig(trimmed);
            dispatchPresetChange();
        }
    }

    function onCloneConfig(text: string): void {
        const trimmed = text.trim();
        if (trimmed.length) {
            state.cloneConfig(trimmed);
            dispatchPresetChange();
        }
    }

    function onRenameConfig(text: string): void {
        state.setCurrentName(text);
    }

    const modals = getContext<Map<string, Modal>>(modalsKey);

    let modalKey: string;
    let modalStartingValue = "";
    let modalTitle = "";
    let modalSuccess: (text: string) => void = noop;

    function promptToAdd() {
        modalTitle = tr.deckConfigAddGroup();
        modalSuccess = onAddConfig;
        modalStartingValue = "";
        modals.get(modalKey)!.show();
    }

    function promptToClone() {
        modalTitle = tr.deckConfigCloneGroup();
        modalSuccess = onCloneConfig;
        modalStartingValue = state.getCurrentName();
        modals.get(modalKey)!.show();
    }

    function promptToRename() {
        modalTitle = tr.deckConfigRenameGroup();
        modalSuccess = onRenameConfig;
        modalStartingValue = state.getCurrentName();
        modals.get(modalKey)!.show();
    }
</script>

<TextInputModal
    title={modalTitle}
    prompt={tr.deckConfigNamePrompt()}
    value={modalStartingValue}
    onOk={modalSuccess}
    bind:modalKey
/>

<StickyContainer --gutter-block="0.5rem" --sticky-borders="0 0 1px" breakpoint="sm">
    <ButtonToolbar class="justify-content-between flex-grow-1" wrap={false}>
        <Select class="flex-grow-1" bind:value {label} on:change={blur}>
            {#each $configList as entry}
                <SelectOption value={entry.idx}>{configLabel(entry)}</SelectOption>
            {/each}
        </Select>

        <SaveButton
            {state}
            on:add={promptToAdd}
            on:clone={promptToClone}
            on:rename={promptToRename}
            on:remove={dispatchPresetChange}
        />
    </ButtonToolbar>
</StickyContainer>
