<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { createEventDispatcher } from "svelte";

    import Badge from "components/Badge.svelte";
    import WithDropdownMenu from "components/WithDropdownMenu.svelte";
    import WithShortcut from "components/WithShortcut.svelte";
    import DropdownMenu from "components/DropdownMenu.svelte";
    import DropdownItem from "components/DropdownItem.svelte";

    import { withSpan, withButton } from "components/helpers";
    import { appendInParentheses } from "./helpers";
    import { dotsIcon } from "./icons";

    const dispatch = createEventDispatcher();
    const copyLabel = "Copy tags";
    const removeLabel = "Remove tags";
</script>

<WithDropdownMenu let:menuId let:createDropdown>
    <div class="dropdown">
        <div class="more-icon">
            <Badge class="me-1" on:mount={withSpan(createDropdown)}
                >{@html dotsIcon}</Badge
            >
        </div>

        <DropdownMenu id={menuId}>
            <WithShortcut shortcut="C" let:createShortcut let:shortcutLabel>
                <DropdownItem
                    on:click={() => dispatch("tagcopy")}
                    on:mount={withButton(createShortcut)}
                    >{appendInParentheses(copyLabel, shortcutLabel)}</DropdownItem
                >
            </WithShortcut>
            <WithShortcut shortcut="Backspace" let:createShortcut let:shortcutLabel>
                <DropdownItem
                    on:click={() => dispatch("tagdelete")}
                    on:mount={withButton(createShortcut)}
                    >{appendInParentheses(removeLabel, shortcutLabel)}</DropdownItem
                >
            </WithShortcut>
        </DropdownMenu>
    </div>
</WithDropdownMenu>

<style lang="scss">
    .more-icon {
        line-height: 1;

        :global(svg) {
            cursor: pointer;
            fill: currentColor;
            opacity: 0.6;
        }

        :global(svg:hover) {
            opacity: 1;
        }
    }
</style>
