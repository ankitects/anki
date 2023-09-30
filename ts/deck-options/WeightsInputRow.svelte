<!--
    Copyright: Ankitects Pty Ltd and contributors
    License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import ConfigInput from "../components/ConfigInput.svelte";
    import RevertButton from "../components/RevertButton.svelte";
    import WeightsInput from "./WeightsInput.svelte";

    export let value: number[];
    export let defaultValue: number[];
    export let defaults: number[];
</script>

<slot />
<ConfigInput>
    <WeightsInput bind:value {defaults} />
    <RevertButton slot="revert" bind:value {defaultValue} />
</ConfigInput>
