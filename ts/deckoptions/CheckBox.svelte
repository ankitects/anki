<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import ConfigEntry from "./ConfigEntry.svelte";
    export let subLabel: string;
    export let value: boolean;
    export let defaultValue: boolean;
</script>

<ConfigEntry label="" wholeLine={true} bind:value {defaultValue}>
    <div>
        <label> <input type="checkbox" bind:checked={value} /> {subLabel} </label>
    </div>
</ConfigEntry>
