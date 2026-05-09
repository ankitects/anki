<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "@generated/ftl";
    import { noop } from "@tslib/functional";
    import type Modal from "bootstrap/js/dist/modal";
    import { createEventDispatcher, getContext } from "svelte";

    import { modalsKey } from "$lib/components/context-keys";
    import Select from "$lib/components/Select.svelte";
    import StickyContainer from "$lib/components/StickyContainer.svelte";

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
    initialValue={modalStartingValue}
    onOk={modalSuccess}
    bind:modalKey
/>

<StickyContainer --gutter-block="0.5rem" --sticky-borders="0 0 1px" breakpoint="sm">
    <div class="button-bar">
        <Select
            class="flex-grow-1 mr1"
            bind:value
            {label}
            list={$configList}
            parser={(entry) => ({
                content: configLabel(entry),
                value: entry.idx,
            })}
            on:change={blur}
        />

        <SaveButton
            {state}
            on:add={promptToAdd}
            on:clone={promptToClone}
            on:rename={promptToRename}
            on:remove={dispatchPresetChange}
        />
    </div>
</StickyContainer>

<style lang="scss">
    .button-bar {
        display: flex;
        flex-wrap: nowrap;
        justify-content: space-between;

        flex-grow: 1;
    }

    /* TODO replace with gap once available (blocked by Qt5 / Chromium 77) */
    :global(.mr1) {
        margin-right: 1rem;
    }
</style>
