<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "lib/i18n";
    import { getContext } from "svelte";
    import { modalsKey } from "components/context-keys";
    import type { DeckOptionsState, ConfigListEntry } from "./lib";
    import type Modal from "bootstrap/js/dist/modal";

    import TextInputModal from "./TextInputModal.svelte";
    import StickyBar from "components/StickyBar.svelte";
    import ButtonToolbar from "components/ButtonToolbar.svelte";
    import Item from "components/Item.svelte";
    import ButtonGroup from "components/ButtonGroup.svelte";
    import ButtonGroupItem from "components/ButtonGroupItem.svelte";

    import SelectButton from "components/SelectButton.svelte";
    import SelectOption from "components/SelectOption.svelte";
    import SaveButton from "./SaveButton.svelte";

    export let state: DeckOptionsState;
    let configList = state.configList;

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
    let modalSuccess = (_text: string) => {};

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

<StickyBar class="g-1">
    <ButtonToolbar class="justify-content-between" size={2.3} wrap={false}>
        <Item>
            <ButtonGroup class="flex-grow-1">
                <ButtonGroupItem>
                    <SelectButton class="flex-grow-1" on:change={blur}>
                        {#each $configList as entry}
                            <SelectOption
                                value={String(entry.idx)}
                                selected={entry.current}
                            >
                                {configLabel(entry)}
                            </SelectOption>
                        {/each}
                    </SelectButton>
                </ButtonGroupItem>
            </ButtonGroup>
        </Item>

        <Item>
            <SaveButton
                {state}
                on:add={promptToAdd}
                on:clone={promptToClone}
                on:rename={promptToRename}
            />
        </Item>
    </ButtonToolbar>
</StickyBar>
