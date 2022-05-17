<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import IconConstrain from "../../components/IconConstrain.svelte";
</script>

<div class="text-button">
    <IconConstrain iconSize={90}>
        <slot name="icon" />
    </IconConstrain>
    <span class="text-button-label"><slot name="label" /></span>
</div>

<style lang="scss">
    .text-button {
        cursor: pointer;
        white-space: nowrap;

        opacity: 0.6;

        &:hover {
            opacity: 1;
        }

        margin-left: 4px;
        margin-right: 4px;

        :global(svg) {
            padding-bottom: 2px;
        }
    }

    .text-button-label {
        font-size: 14px;
        padding-right: 4px;
    }
</style>
