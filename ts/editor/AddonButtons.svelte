<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import ButtonGroup from "components/ButtonGroup.svelte";
    import ButtonGroupItem from "components/ButtonGroupItem.svelte";

    export let buttons: string[];
</script>

<ButtonGroup>
    {#each buttons as button}
        <ButtonGroupItem>
            {@html button}
        </ButtonGroupItem>
    {/each}
</ButtonGroup>
