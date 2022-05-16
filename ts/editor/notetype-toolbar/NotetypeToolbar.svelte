<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import ButtonToolbar from "../../components/ButtonToolbar.svelte";
    import IconConstrain from "../../components/IconConstrain.svelte";
    import DeckSelector from "./DeckSelector.svelte";
    import { cardtypeIcon, fieldIcon } from "./icons";
    import NotetypeButtons from "./NotetypeButtons.svelte";
    import NotetypeSelector from "./NotetypeSelector.svelte";

    export let size: number = 1.6;
    export let wrap: boolean = false;
</script>

<ButtonToolbar {size} {wrap}>
    <NotetypeSelector />
    <NotetypeButtons />
    <div class="cardtype-icon" on:mousedown|preventDefault>
        <IconConstrain iconSize={90}>
            {@html cardtypeIcon}
        </IconConstrain>
        <span>Cards...</span>
    </div>
    <div class="field-icon" on:mousedown|preventDefault>
        <IconConstrain iconSize={90}>
            {@html fieldIcon}
        </IconConstrain>
        <span>Fields...</span>
    </div>
    <DeckSelector />
</ButtonToolbar>

<style lang="scss">
    .cardtype-icon, .field-icon {
        margin-right: 0.15rem;

        :global(svg) {
            padding-bottom: 2px;
            cursor: pointer;
            fill: currentColor;
            opacity: 0.6;
        }
        :global(span) {
            padding-bottom: 2px;
            cursor: pointer;
            fill: currentColor;
            opacity: 0.6;
        }

        &:hover {
            :global(svg), :global(span) {
                opacity: 1;
            }
        }
    }
</style>
