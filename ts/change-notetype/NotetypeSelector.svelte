<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import type { ChangeNotetypeState } from "./lib";
    import StickyContainer from "../components/StickyContainer.svelte";
    import ButtonToolbar from "../components/ButtonToolbar.svelte";
    import ButtonGroup from "../components/ButtonGroup.svelte";
    import LabelButton from "../components/LabelButton.svelte";
    import Badge from "../components/Badge.svelte";
    import { arrowRightIcon, arrowLeftIcon } from "./icons";
    import SelectButton from "../components/SelectButton.svelte";
    import SelectOption from "../components/SelectOption.svelte";
    import SaveButton from "./SaveButton.svelte";

    export let state: ChangeNotetypeState;
    let notetypes = state.notetypes;
    let info = state.info;

    async function blur(event: Event): Promise<void> {
        await state.setTargetNotetypeIndex(
            parseInt((event.target! as HTMLSelectElement).value),
        );
    }
</script>

<StickyContainer
    --gutter-block="0.1rem"
    --gutter-inline="0.25rem"
    --sticky-borders="0 0 1px"
>
    <ButtonToolbar class="justify-content-between" size={2.3} wrap={false}>
        <LabelButton disabled={true}>
            {$info.oldNotetypeName}
        </LabelButton>
        <Badge iconSize={70}>
            {#if window.getComputedStyle(document.body).direction == "rtl"}
                {@html arrowLeftIcon}
            {:else}
                {@html arrowRightIcon}
            {/if}
        </Badge>
        <ButtonGroup class="flex-grow-1">
            <SelectButton class="flex-grow-1" on:change={blur}>
                {#each $notetypes as entry}
                    <SelectOption value={String(entry.idx)} selected={entry.current}>
                        {entry.name}
                    </SelectOption>
                {/each}
            </SelectButton>
        </ButtonGroup>

        <SaveButton {state} />
    </ButtonToolbar>
</StickyContainer>
