<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import IconConstrain from "../../components/IconConstrain.svelte";
    import { bridgeCommand } from "../../lib/bridgecommand";
    import * as tr from "../../lib/ftl";
    import { fieldIcon } from "./icons";
</script>

<div
    class="fields-button"
    title={tr.editingCustomizeFields()}
    on:mousedown|preventDefault
    on:click={() => bridgeCommand("fields")}
>
    <IconConstrain iconSize={90}>
        {@html fieldIcon}
    </IconConstrain>
    <span>{tr.editingFields()}</span>
</div>

<style lang="scss">
    .fields-button {
        cursor: pointer;
        white-space: nowrap;

        opacity: 0.6;

        &:hover {
            opacity: 1;
        }

        margin-left: 4px;
        margin-right: 4px;

        span {
            font-size: 14px;
            padding-right: 4px;
        }

        :global(svg) {
            padding-bottom: 2px;
        }
    }
</style>
