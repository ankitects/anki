<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import type { EditingArea } from "./editingArea";
    import * as tr from "lib/i18n";

    import CommandIconButton from "components/CommandIconButton.svelte";
    import IconButton from "components/IconButton.svelte";
    import ButtonGroup from "components/ButtonGroup.svelte";
    import ButtonDropdown from "components/ButtonDropdown.svelte";
    import WithDropdownMenu from "components/WithDropdownMenu.svelte";

    import { getListItem } from "./helpers";
    import {
        ulIcon,
        olIcon,
        listOptionsIcon,
        justifyFullIcon,
        justifyLeftIcon,
        justifyRightIcon,
        justifyCenterIcon,
        indentIcon,
        outdentIcon,
    } from "./icons";

    export let api = {};

    function outdentListItem() {
        const currentField = document.activeElement as EditingArea;
        if (getListItem(currentField.shadowRoot!)) {
            document.execCommand("outdent");
        }
    }

    function indentListItem() {
        const currentField = document.activeElement as EditingArea;
        if (getListItem(currentField.shadowRoot!)) {
            document.execCommand("indent");
        }
    }
</script>

<ButtonDropdown id="listFormatting">
    <ButtonGroup id="justify" {api}>
        <CommandIconButton command="justifyLeft" tooltip={tr.editingAlignLeft()}>
            {@html justifyLeftIcon}
        </CommandIconButton>

        <CommandIconButton command="justifyCenter" tooltip={tr.editingCenter()}>
            {@html justifyCenterIcon}
        </CommandIconButton>

        <CommandIconButton command="justifyCenter" tooltip={tr.editingCenter()}>
            {@html justifyCenterIcon}
        </CommandIconButton>

        <CommandIconButton command="justifyRight" tooltip={tr.editingAlignRight()}>
            {@html justifyRightIcon}
        </CommandIconButton>

        <CommandIconButton command="justifyFull" tooltip={tr.editingJustify()}>
            {@html justifyFullIcon}
        </CommandIconButton>
    </ButtonGroup>

    <ButtonGroup id="indentation" {api}>
        <IconButton on:click={outdentListItem} tooltip={tr.editingOutdent}>
            {@html outdentIcon}
        </IconButton>

        <IconButton on:click={indentListItem} tooltip={tr.editingIndent}>
            {@html indentIcon}
        </IconButton>
    </ButtonGroup>
</ButtonDropdown>

<ButtonGroup id="blockFormatting" {api}>
    <CommandIconButton
        command="insertUnorderedList"
        tooltip={tr.editingUnorderedList()}>
        {@html ulIcon}
    </CommandIconButton>

    <CommandIconButton command="insertOrderedList" tooltip={tr.editingOrderedList()}>
        {@html olIcon}
    </CommandIconButton>

    <WithDropdownMenu menuId="listFormatting">
        <IconButton>
            {@html listOptionsIcon}
        </IconButton>
    </WithDropdownMenu>
</ButtonGroup>
