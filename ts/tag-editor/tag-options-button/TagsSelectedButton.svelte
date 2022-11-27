<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { createEventDispatcher, getContext } from "svelte";

    import DropdownItem from "../../components/DropdownItem.svelte";
    import IconConstrain from "../../components/IconConstrain.svelte";
    import Popover from "../../components/Popover.svelte";
    import WithFloating from "../../components/WithFloating.svelte";
    import { tagActionsShortcutsKey } from "@tslib/context-keys";
    import * as tr from "@tslib/ftl";
    import { getPlatformString } from "@tslib/shortcuts";
    import { dotsIcon } from "./icons";

    const dispatch = createEventDispatcher();

    let show = false;

    const { selectAllShortcut, copyShortcut, removeShortcut } =
        getContext<Record<string, string>>(tagActionsShortcutsKey);
</script>

<WithFloating
    {show}
    preferredPlacement="top"
    portalTarget={document.body}
    shift={0}
    let:asReference
>
    <div class="tags-selected-button" use:asReference on:click={() => (show = !show)}>
        <IconConstrain>{@html dotsIcon}</IconConstrain>
    </div>

    <Popover slot="floating">
        <DropdownItem on:click={() => dispatch("tagselectall")}>
            {tr.editingTagsSelectAll()} ({getPlatformString(selectAllShortcut)})
        </DropdownItem>

        <DropdownItem on:click={() => dispatch("tagcopy")}
            >{tr.editingTagsCopy()} ({getPlatformString(copyShortcut)})</DropdownItem
        >

        <DropdownItem on:click={() => dispatch("tagdelete")}
            >{tr.editingTagsRemove()} ({getPlatformString(
                removeShortcut,
            )})</DropdownItem
        >
    </Popover>
</WithFloating>

<style lang="scss">
    .tags-selected-button {
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
