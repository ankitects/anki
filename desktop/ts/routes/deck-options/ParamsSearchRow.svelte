<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import ConfigInput from "$lib/components/ConfigInput.svelte";

    export let value: string;
    export let placeholder: string;
</script>

<ConfigInput>
    <input type="text" bind:value {placeholder} class="w-100 mb-1" />
</ConfigInput>
