<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import WithShortcut from "../components/WithShortcut.svelte";
    import Badge from "../components/Badge.svelte";

    import { withSpan } from "../components/helpers";
    import { appendInParentheses } from "./helpers";
    import { tagIcon, addTagIcon } from "./icons";

    const tooltip = "Add tag";
</script>

<WithShortcut shortcut="Control+Shift+T" let:createShortcut let:shortcutLabel>
    <div class="add-icon">
        <Badge
            class="d-flex me-1"
            tooltip={appendInParentheses(tooltip, shortcutLabel)}
            on:click
            on:mount={withSpan(createShortcut)}
        >
            {@html tagIcon}
            {@html addTagIcon}
        </Badge>
    </div>
</WithShortcut>

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
