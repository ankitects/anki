<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "@tslib/ftl";
    import { getPlatformString } from "@tslib/shortcuts";
    import { createEventDispatcher } from "svelte";
    
    import DropdownItem from "../../components/DropdownItem.svelte";
    import IconConstrain from "../../components/IconConstrain.svelte";
    import Popover from "../../components/Popover.svelte";
    import Shortcut from "../../components/Shortcut.svelte";
    import WithFloating from "../../components/WithFloating.svelte";
    import { dotsIcon } from "./icons";

    const dispatch = createEventDispatcher();

    let show = false;

    const allShortcut = "Control+A";
    const copyShortcut = "Control+C";
    const removeShortcut = "Backspace";
</script>

<WithFloating
    {show}
    placement="top"
    portalTarget={document.body}
    shift={0}
    let:asReference
>
    <div class="tags-selected-button" use:asReference on:click={() => (show = !show)}>
        <IconConstrain>{@html dotsIcon}</IconConstrain>
    </div>

    <Popover slot="floating">
        <DropdownItem on:click={() => dispatch("tagselectall")}>
            {tr.editingTagsSelectAll()} ({getPlatformString(allShortcut)})
        </DropdownItem>
        <Shortcut
            keyCombination={allShortcut}
            on:action={() => dispatch("tagselectall")}
        />

        <DropdownItem on:click={() => dispatch("tagcopy")}
            >{tr.editingTagsCopy()} ({getPlatformString(copyShortcut)})</DropdownItem
        >
        <Shortcut keyCombination={copyShortcut} on:action={() => dispatch("tagcopy")} />

        <DropdownItem on:click={() => dispatch("tagdelete")}
            >{tr.editingTagsRemove()} ({getPlatformString(
                removeShortcut,
            )})</DropdownItem
        >
        <Shortcut
            keyCombination={removeShortcut}
            on:action={() => dispatch("tagdelete")}
        />
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
