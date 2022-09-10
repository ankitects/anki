<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import ButtonGroup from "../../components/ButtonGroup.svelte";
    import ButtonGroupItem from "../../components/ButtonGroupItem.svelte";

    export let buttons: string[];
</script>

<ButtonGroup>
    {#each buttons as button}
        <ButtonGroupItem>
            <div>{@html button}</div>
        </ButtonGroupItem>
    {/each}
</ButtonGroup>

<style lang="scss">
    @use "sass/button-mixins" as button;
    div {
        @include button.btn-border-radius;
    }
</style>
