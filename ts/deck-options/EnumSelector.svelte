<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import Select from "../components/Select.svelte";
    import SelectOption from "../components/SelectOption.svelte";
    export let options: string[] = [];
    export let value = 0;
</script>

<Select current={options[value]}>
    {#each options as option, idx}
        <SelectOption on:select={() => (value = idx)}>{option}</SelectOption>
    {/each}
</Select>
