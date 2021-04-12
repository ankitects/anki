<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import ConfigEntry from "./ConfigEntry.svelte";

    export let label: string;
    export let subLabel: string;
    export let value: number;
    export let min = 1;
    export let max = 9999;
    export let warn = false;
    export let defaultValue: number = 0;

    function blur() {
        if (value > max) {
            value = max;
        } else if (value < min) {
            value = min;
        }
    }
</script>

<ConfigEntry {label} {subLabel} bind:value {defaultValue}>
    <input
        type="number"
        {min}
        {max}
        bind:value
        on:blur={blur}
        class:warn
        class="form-control" />
</ConfigEntry>
