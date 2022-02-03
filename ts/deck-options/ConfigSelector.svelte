<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import type Modal from "bootstrap/js/dist/modal";
    import { getContext } from "svelte";

    import ButtonGroup from "../components/ButtonGroup.svelte";
    import ButtonToolbar from "../components/ButtonToolbar.svelte";
    import { modalsKey } from "../components/context-keys";
    import SelectButton from "../components/SelectButton.svelte";
    import SelectOption from "../components/SelectOption.svelte";
    import StickyContainer from "../components/StickyContainer.svelte";
    import * as tr from "../lib/ftl";
    import { noop } from "../lib/functional";
    import type { ConfigListEntry, DeckOptionsState } from "./lib";
    import SaveButton from "./SaveButton.svelte";
    import TextInputModal from "./TextInputModal.svelte";

    export let state: DeckOptionsState;
    const configList = state.configList;

    function configLabel(entry: ConfigListEntry): string {
        const count = tr.deckConfigUsedByDecks({ decks: entry.useCount });
        return `${entry.name} (${count})`;
    }

    function blur(event: Event): void {
        state.setCurrentIndex(parseInt((event.target! as HTMLSelectElement).value));
    }

    function onAddConfig(text: string): void {
        const trimmed = text.trim();
        if (trimmed.length) {
            state.addConfig(trimmed);
        }
    }

    function onCloneConfig(text: string): void {
        const trimmed = text.trim();
        if (trimmed.length) {
            state.cloneConfig(trimmed);
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
    <ButtonToolbar class="justify-content-between" size={2.3} wrap={false}>
        <ButtonGroup class="flex-grow-1">
            <SelectButton
                class="flex-grow-1"
                on:change={blur}
                --border-left-radius="5px"
                --border-right-radius="5px"
            >
                {#each $configList as entry}
                    <SelectOption value={String(entry.idx)} selected={entry.current}>
                        {configLabel(entry)}
                    </SelectOption>
                {/each}
            </SelectButton>
        </ButtonGroup>

        <SaveButton
            {state}
            on:add={promptToAdd}
            on:clone={promptToClone}
            on:rename={promptToRename}
        />
    </ButtonToolbar>
</StickyContainer>
