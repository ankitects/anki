<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import ButtonToolbar from "$lib/components/ButtonToolbar.svelte";
    import Icon from "$lib/components/Icon.svelte";
    import { arrowLeftIcon, arrowRightIcon } from "$lib/components/icons";
    import Select from "$lib/components/Select.svelte";

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
    <div class="d-flex flex-row w-100">
        <Select label={$info.oldNotetypeName} value={1} list={[1]} disabled={true} />
        <div class="arrow-container">
            {#if window.getComputedStyle(document.body).direction == "rtl"}
                <Icon icon={arrowLeftIcon} />
            {:else}
                <Icon icon={arrowRightIcon} />
            {/if}
        </div>
        <Select list={options} bind:value {label} />
    </div>
    <SaveButton {state} />
</ButtonToolbar>

<style lang="scss">
    .arrow-container {
        margin: 0 0.5em;
    }
</style>
