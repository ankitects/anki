<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { createEventDispatcher } from "svelte";

    import Badge from "../../components/Badge.svelte";
    import DropdownItem from "../../components/DropdownItem.svelte";
    import DropdownMenu from "../../components/DropdownMenu.svelte";
    import { withSpan } from "../../components/helpers";
    import Shortcut from "../../components/Shortcut.svelte";
    import WithDropdown from "../../components/WithDropdown.svelte";
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

<WithDropdown let:createDropdown>
    <div class="more-icon">
        <Badge class="me-1" on:mount={withSpan(createDropdown)}>{@html dotsIcon}</Badge>

        <DropdownMenu>
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
            <Shortcut
                keyCombination={copyShortcut}
                on:action={() => dispatch("tagcopy")}
            />

            <DropdownItem on:click={() => dispatch("tagdelete")}
                >{removeLabel} ({getPlatformString(removeShortcut)})</DropdownItem
            >
            <Shortcut
                keyCombination={removeShortcut}
                on:action={() => dispatch("tagdelete")}
            />
        </DropdownMenu>
    </div>
</WithDropdown>

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
