<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import ButtonGroup from "../../components/ButtonGroup.svelte";

    export let buttons: string[];

    const radius = 5;

    function getLeftRadius(index: number): number {
        if (index === 0) {
            return radius;
        }
        return 0;
    }

    function getRightRadius(index: number, length: number): number {
        if (length === 1 || index === length - 1) {
            return radius;
        }
        return 0;
    }
</script>

<ButtonGroup>
    {#each buttons as button, index}
        <div
            style:--border-left-radius="{getLeftRadius(index)}px"
            style:--border-right-radius="{getRightRadius(index, buttons.length)}px"
        >
            {@html button}
        </div>
    {/each}
</ButtonGroup>

<style lang="scss">
    div {
        display: contents;
    }
</style>
