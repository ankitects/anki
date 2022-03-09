<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { createEventDispatcher } from "svelte";

    import IconConstrain from "../../components/IconConstrain.svelte";
    import DropdownItem from "../../components/DropdownItem.svelte";
    import Popover from "../../components/Popover.svelte";
    import Shortcut from "../../components/Shortcut.svelte";
    import WithFloating from "../../components/WithFloating.svelte";
    import { getPlatformString } from "../../lib/shortcuts";
    import { dotsIcon } from "./icons";

    const dispatch = createEventDispatcher();

    const allLabel = "Select all tags";
    const allShortcut = "Control+A";
    const copyLabel = "Copy tags";
    const copyShortcut = "Control+C";
    const removeLabel = "Remove tags";
    const removeShortcut = "Backspace";
</script>

<WithFloating placement="top">
    <div slot="reference" class="more-icon" let:toggle on:click={toggle}>
        <IconConstrain>{@html dotsIcon}</IconConstrain>
    </div>

    <Popover slot="floating">
        <DropdownItem
            on:click={(event) => {
                dispatch("tagselectall");
                event.stopImmediatePropagation();
            }}>{allLabel} ({getPlatformString(allShortcut)})</DropdownItem
        >
        <Shortcut
            keyCombination={allShortcut}
            on:action={(event) => {
                dispatch("tagselectall");
                event.stopImmediatePropagation();
            }}
        />

        <DropdownItem on:click={() => dispatch("tagcopy")}
            >{copyLabel} ({getPlatformString(copyShortcut)})</DropdownItem
        >
        <Shortcut keyCombination={copyShortcut} on:action={() => dispatch("tagcopy")} />

        <DropdownItem on:click={() => dispatch("tagdelete")}
            >{removeLabel} ({getPlatformString(removeShortcut)})</DropdownItem
        >
        <Shortcut
            keyCombination={removeShortcut}
            on:action={() => dispatch("tagdelete")}
        />
    </Popover>
</WithFloating>

<style lang="scss">
    .more-icon {
        line-height: 1;

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
