<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { createEventDispatcher } from "svelte";
    import Badge from "../../components/Badge.svelte";
    import Shortcut from "../../components/Shortcut.svelte";
    import { getPlatformString } from "../../lib/shortcuts";
    import { tagIcon, addTagIcon } from "./icons";

    const dispatch = createEventDispatcher();
    const tooltip = "Add tag";
    const keyCombination = "Control+Shift+T";

    function appendTag(): void {
        dispatch("tagappend");
    }
</script>

<div class="add-icon">
    <Badge
        class="d-flex me-1"
        tooltip="{tooltip} ({getPlatformString(keyCombination)})"
        on:click={appendTag}
    >
        {@html tagIcon}
        {@html addTagIcon}
    </Badge>

    <Shortcut {keyCombination} on:action={appendTag} />
</div>

<style lang="scss">
    .add-icon {
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
