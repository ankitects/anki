<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import Badge from "../components/Badge.svelte";
    import ButtonGroup from "../components/ButtonGroup.svelte";
    import ButtonToolbar from "../components/ButtonToolbar.svelte";
    import LabelButton from "../components/LabelButton.svelte";
    import Select from "../components/Select.svelte";
    import SelectOption from "../components/SelectOption.svelte";
    import StickyContainer from "../components/StickyContainer.svelte";
    import { arrowLeftIcon, arrowRightIcon } from "./icons";
    import type { ChangeNotetypeState } from "./lib";
    import SaveButton from "./SaveButton.svelte";

    export let state: ChangeNotetypeState;
    const notetypes = state.notetypes;
    const info = state.info;
    let value: number = 0;

    $: state.setTargetNotetypeIndex(value);
    $: options = Array.from($notetypes, (notetype) => notetype.name);
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
            <Select
                class="flex-grow-1"
                current={options[value]}
            >
                {#each options as option, idx}
                    <SelectOption on:select={() => (value = idx)}
                        >{option}
                    </SelectOption>
                {/each}
            </Select>
        </ButtonGroup>

        <SaveButton {state} />
    </ButtonToolbar>
</StickyContainer>
