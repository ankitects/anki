<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import WithShortcut from "components/WithShortcut.svelte";
    import Badge from "components/Badge.svelte";

    import { withSpan } from "components/helpers";
    import { appendInParentheses } from "./helpers";
    import { tagIcon, addTagIcon } from "./icons";

    let theTagIcon = tagIcon;

    const tooltip = "Add tag";
</script>

<WithShortcut shortcut="Control+Shift+T" let:createShortcut let:shortcutLabel>
    <Badge
        class="add-icon d-flex me-1"
        tooltip={appendInParentheses(tooltip, shortcutLabel)}
        on:click
        on:mouseenter={() => (theTagIcon = addTagIcon)}
        on:mouseleave={() => (theTagIcon = tagIcon)}
        on:mount={withSpan(createShortcut)}>{@html theTagIcon}</Badge
    >
</WithShortcut>

<style lang="scss">
    :global(.add-icon > svg) {
        cursor: pointer;
        opacity: 0.6;
    }

    :global(.add-icon > svg:hover) {
        opacity: 1;
    }
</style>
