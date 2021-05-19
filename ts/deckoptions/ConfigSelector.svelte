<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "lib/i18n";
    import type { DeckOptionsState, ConfigListEntry } from "./lib";

    import WithTheming from "components/WithTheming.svelte";
    import StickyBar from "components/StickyBar.svelte";
    import ButtonToolbar from "components/ButtonToolbar.svelte";
    import ButtonToolbarItem from "components/ButtonToolbarItem.svelte";
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
</script>

<StickyBar>
    <WithTheming style="--toolbar-size: 35px; --toolbar-wrap: nowrap">
        <ButtonToolbar class="justify-content-between">
            <ButtonToolbarItem>
                <ButtonGroup class="flex-grow-1">
                    <ButtonGroupItem>
                        <SelectButton class="flex-grow-1" on:change={blur}>
                            {#each $configList as entry}
                                <SelectOption
                                    value={String(entry.idx)}
                                    selected={entry.current}>
                                    {configLabel(entry)}
                                </SelectOption>
                            {/each}
                        </SelectButton>
                    </ButtonGroupItem>
                </ButtonGroup>
            </ButtonToolbarItem>

            <ButtonToolbarItem>
                <SaveButton {state} />
            </ButtonToolbarItem>
        </ButtonToolbar>
    </WithTheming>
</StickyBar>
