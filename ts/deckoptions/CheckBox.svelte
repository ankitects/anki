<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import ConfigEntry from "./ConfigEntry.svelte";
    import HelpPopup from "./HelpPopup.svelte";
    export let label: string;
    export let value: boolean;
    export let defaultValue: boolean;
    export let tooltip = "";
    export let id: string | undefined = undefined;
</script>

<ConfigEntry {id} label="" wholeLine={true} bind:value {defaultValue}>
    <div class="checkbox-outer">
        <label> <input type="checkbox" bind:checked={value} /> {label} </label>
        {#if tooltip}
            <HelpPopup html={tooltip} />
        {/if}
    </div>
</ConfigEntry>

<style lang="scss">
    .checkbox-outer {
        margin-top: 0.5em;
    }
</style>
