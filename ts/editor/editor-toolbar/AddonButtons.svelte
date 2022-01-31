<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import ButtonGroup from "../../components/ButtonGroup.svelte";

    export let buttons: string[];

    const radius = "5px";
    function getBorderRadius(index: number, length: number): string {
        if (index === 0 && length === 1) {
            return `--border-left-radius: ${radius}; --border-right-radius: ${radius}; `;
        } else if (index === 0) {
            return `--border-left-radius: ${radius}; --border-right-radius: 0; `;
        } else if (index === length - 1) {
            return `--border-left-radius: 0; --border-right-radius: ${radius}; `;
        } else {
            return "--border-left-radius: 0; --border-right-radius: 0; ";
        }
    }
</script>

<ButtonGroup>
    {#each buttons as button, index}
        <div style={getBorderRadius(index, buttons.length)}>
            {@html button}
        </div>
    {/each}
</ButtonGroup>

<style lang="scss">
    div {
        display: contents;
    }
</style>
