<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import DropdownItem from "$lib/components/DropdownItem.svelte";
    import Shortcut from "$lib/components/Shortcut.svelte";

    export let shortcut: string = "";
    export let onClick = () => {};
</script>

<Shortcut keyCombination={shortcut} on:keydown={onClick}></Shortcut>
<DropdownItem on:click={onClick}><slot /></DropdownItem>
