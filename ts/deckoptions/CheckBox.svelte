<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import RevertButton from "./RevertButton.svelte";

    export let label: string;
    export let value: boolean;
    export let defaultValue: boolean;
</script>

<label> <input type="checkbox" bind:checked={value} /> {label} </label>
<RevertButton bind:value {defaultValue} />
