<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import Select from "../components/Select.svelte";
    import SelectOption from "../components/SelectOption.svelte";

    export let options: string[] = [];
    export let disabled: number[] = [];
    export let value = 0;

    $: label = options[value];
</script>

<Select bind:value {label}>
    {#each options as option, idx}
        <SelectOption value={idx} disabled={disabled.includes(idx)}
            >{option}</SelectOption
        >
    {/each}
</Select>
