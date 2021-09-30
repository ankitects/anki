<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { createEventDispatcher } from "svelte";

    import Badge from "../components/Badge.svelte";
    import WithDropdown from "../components/WithDropdown.svelte";
    import WithShortcut from "../components/WithShortcut.svelte";
    import DropdownMenu from "../components/DropdownMenu.svelte";
    import DropdownItem from "../components/DropdownItem.svelte";

    import { withSpan, withButton } from "../components/helpers";
    import { appendInParentheses } from "./helpers";
    import { dotsIcon } from "./icons";

    const dispatch = createEventDispatcher();

    const allLabel = "Select all tags";
    const copyLabel = "Copy tags";
    const removeLabel = "Remove tags";
</script>

<WithDropdown let:createDropdown>
    <div class="more-icon">
        <Badge class="me-1" on:mount={withSpan(createDropdown)}>{@html dotsIcon}</Badge>

        <DropdownMenu>
            <WithShortcut shortcut="Control+A" let:createShortcut let:shortcutLabel>
                <DropdownItem
                    on:click={(event) => {
                        dispatch("tagselectall");
                        event.stopImmediatePropagation();
                    }}
                    on:mount={withButton(createShortcut)}
                    >{appendInParentheses(allLabel, shortcutLabel)}</DropdownItem
                >
            </WithShortcut>
            <WithShortcut shortcut="Control+C" let:createShortcut let:shortcutLabel>
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
