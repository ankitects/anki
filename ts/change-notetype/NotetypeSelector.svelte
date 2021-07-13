<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import type { ChangeNotetypeState } from "./lib";

    import StickyBar from "components/StickyBar.svelte";
    import ButtonToolbar from "components/ButtonToolbar.svelte";
    import Item from "components/Item.svelte";
    import ButtonGroup from "components/ButtonGroup.svelte";
    import ButtonGroupItem from "components/ButtonGroupItem.svelte";

    import SelectButton from "components/SelectButton.svelte";
    import SelectOption from "components/SelectOption.svelte";
    import SaveButton from "./SaveButton.svelte";

    export let state: ChangeNotetypeState;
    let notetypes = state.notetypes;

    async function blur(event: Event): Promise<void> {
        await state.setTargetNotetypeIndex(
            parseInt((event.target! as HTMLSelectElement).value)
        );
    }
</script>

<StickyBar>
    <ButtonToolbar class="justify-content-between" size={2.3} wrap={false}>
        <Item>
            <ButtonGroup class="flex-grow-1">
                <ButtonGroupItem>
                    <SelectButton class="flex-grow-1" on:change={blur}>
                        {#each $notetypes as entry}
                            <SelectOption
                                value={String(entry.idx)}
                                selected={entry.current}
                            >
                                {entry.name}
                            </SelectOption>
                        {/each}
                    </SelectButton>
                </ButtonGroupItem>
            </ButtonGroup>
        </Item>

        <Item>
            <SaveButton {state} />
        </Item>
    </ButtonToolbar>
</StickyBar>
