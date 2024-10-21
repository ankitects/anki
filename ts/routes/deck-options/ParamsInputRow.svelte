<!--
    Copyright: Ankitects Pty Ltd and contributors
    License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import ConfigInput from "$lib/components/ConfigInput.svelte";
    import RevertButton from "$lib/components/RevertButton.svelte";

    import ParamsInput from "./ParamsInput.svelte";

    export let value: number[];
    export let defaultValue: number[];
    export let defaults: number[];
</script>

<slot />
<ConfigInput>
    <ParamsInput bind:value {defaults} />
    <RevertButton slot="revert" bind:value {defaultValue} />
</ConfigInput>
