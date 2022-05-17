<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import IconConstrain from "../../components/IconConstrain.svelte";
    import { bridgeCommand } from "../../lib/bridgecommand";
    import * as tr from "../../lib/ftl";
    import { getPlatformString } from "../../lib/shortcuts";
    import { cardtypeIcon } from "./icons";

    const keyCombination = "Control+L";
</script>

<div
    class="fields-button"
    title="{tr.editingCustomizeCardTemplates()} ({getPlatformString(keyCombination)})"
    on:mousedown|preventDefault
    on:click={() => bridgeCommand("cards")}
>
    <IconConstrain iconSize={90}>
        {@html cardtypeIcon}
    </IconConstrain>
    <span>{tr.editingCardtype()}</span>
</div>

<style lang="scss">
    .fields-button {
        cursor: pointer;

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
</style>
