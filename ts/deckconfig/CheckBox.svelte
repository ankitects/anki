<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import ConfigEntry from "./ConfigEntry.svelte";

    export let label: string;
    export let subLabel: string;
    export let value: boolean;
    export let defaultValue: boolean;
</script>

<style>
</style>

<ConfigEntry {label} bind:value {defaultValue}>
    <label> <input type="checkbox" bind:checked={value} /> {subLabel} </label>
</ConfigEntry>
