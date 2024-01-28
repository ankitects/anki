<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import Badge from "$lib/components/Badge.svelte";
    import ButtonToolbar from "$lib/components/ButtonToolbar.svelte";
    import LabelButton from "$lib/components/LabelButton.svelte";
    import Select from "$lib/components/Select.svelte";
    import { arrowLeftIcon, arrowRightIcon } from "$lib/components/icons";
    import type { ChangeNotetypeState } from "./lib";
    import SaveButton from "./SaveButton.svelte";

    export let state: ChangeNotetypeState;
    const notetypes = state.notetypes;
    const info = state.info;

    let value = $notetypes.findIndex((e) => e.current);
    $: options = Array.from($notetypes, (notetype) => notetype.name);
    $: label = options[value];

    $: state.setTargetNotetypeIndex(value);
</script>

<ButtonToolbar class="justify-content-between" wrap={false}>
    <LabelButton ellipsis disabled={true}>
        {$info.oldNotetypeName}
    </LabelButton>
    <Badge iconSize={70}>
        {#if window.getComputedStyle(document.body).direction == "rtl"}
            {@html arrowLeftIcon}
        {:else}
            {@html arrowRightIcon}
        {/if}
    </Badge>
    <Select class="flex-grow-1" list={options} bind:value {label} />

    <SaveButton {state} />
</ButtonToolbar>
