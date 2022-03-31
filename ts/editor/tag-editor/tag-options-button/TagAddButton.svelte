<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { createEventDispatcher } from "svelte";

    import IconConstrain from "../../../components/IconConstrain.svelte";
    import Shortcut from "../../../components/Shortcut.svelte";
    import * as tr from "../../../lib/ftl";
    import { getPlatformString } from "../../../lib/shortcuts";
    import { addTagIcon, tagIcon } from "./icons";

    const dispatch = createEventDispatcher<{ tagappend: CustomEvent<void> }>();

    const keyCombination = "Control+Shift+T";
</script>

<div
    class="tag-add-button"
    title="{tr.editingTagsAdd()} ({getPlatformString(keyCombination)})"
    on:click={() => dispatch("tagappend")}
>
    <IconConstrain>
        {@html tagIcon}
        {@html addTagIcon}
    </IconConstrain>
</div>

<Shortcut {keyCombination} on:action={() => dispatch("tagappend")} />

<style lang="scss">
    .tag-add-button {
        line-height: 1;

        :global(svg:last-child) {
            display: none;
        }

        &:hover {
            :global(svg:first-child) {
                display: none;
            }

            :global(svg:last-child) {
                display: block;
            }
        }

        :global(svg) {
            padding-bottom: 2px;
            cursor: pointer;
            fill: currentColor;
            opacity: 0.6;
        }

        :global(svg:hover) {
            opacity: 1;
        }
    }
</style>
