<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import IconConstrain from "../../components/IconConstrain.svelte";

    export let ghost = false;
</script>

<div class="text-button" class:text-button-ghost={ghost}>
    <IconConstrain iconSize={90}>
        <slot name="icon" />
    </IconConstrain>
    <span class="text-button-label"><slot name="label" /></span>
</div>

<style lang="scss">
    .text-button {
        cursor: pointer;
        white-space: nowrap;

        opacity: 0.9;
        margin-left: 4px;
        margin-right: 4px;

        &:hover {
            opacity: 1;
        }

        :global(svg) {
            padding-bottom: 2px;
        }

        border: 1px solid transparent;
        border-radius: 5px;
    }

    .text-button-label {
        font-size: 14px;
        font-weight: 500;
        padding-right: 5px;
    }

    .text-button-ghost {
        border-color: white;
    }
</style>
