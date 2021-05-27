<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import type { EditingArea } from "./editingArea";
    import * as tr from "lib/i18n";

    import ButtonGroup from "components/ButtonGroup.svelte";
    import ButtonGroupItem from "components/ButtonGroupItem.svelte";
    import IconButton from "components/IconButton.svelte";
    import ButtonDropdown from "components/ButtonDropdown.svelte";
    import SectionItem from "components/SectionItem.svelte";
    import WithDropdownMenu from "components/WithDropdownMenu.svelte";
    import OnlyEditable from "./OnlyEditable.svelte";
    import CommandIconButton from "./CommandIconButton.svelte";

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

<ButtonGroup {api}>
    <ButtonGroupItem>
        <CommandIconButton
            key="insertUnorderedList"
            tooltip={tr.editingUnorderedList()}
            withoutShortcut>{@html ulIcon}</CommandIconButton
        >
    </ButtonGroupItem>

    <ButtonGroupItem>
        <CommandIconButton
            key="insertOrderedList"
            tooltip={tr.editingOrderedList()}
            withoutShortcut>{@html olIcon}</CommandIconButton
        >
    </ButtonGroupItem>

    <ButtonGroupItem>
        <WithDropdownMenu let:createDropdown let:menuId>
            <OnlyEditable let:disabled>
                <IconButton {disabled} on:mount={createDropdown}>
                    {@html listOptionsIcon}
                </IconButton>
            </OnlyEditable>

            <ButtonDropdown id={menuId}>
                <SectionItem id="justify">
                    <ButtonGroup>
                        <ButtonGroupItem>
                            <CommandIconButton
                                key="justifyLeft"
                                tooltip={tr.editingAlignLeft()}
                                withoutShortcut
                                >{@html justifyLeftIcon}</CommandIconButton
                            >
                        </ButtonGroupItem>

                        <ButtonGroupItem>
                            <CommandIconButton
                                key="justifyCenter"
                                tooltip={tr.editingCenter()}
                                withoutShortcut
                                >{@html justifyCenterIcon}</CommandIconButton
                            >
                        </ButtonGroupItem>

                        <ButtonGroupItem>
                            <CommandIconButton
                                key="justifyRight"
                                tooltip={tr.editingAlignRight()}
                                withoutShortcut
                                >{@html justifyRightIcon}</CommandIconButton
                            >
                        </ButtonGroupItem>

                        <ButtonGroupItem>
                            <CommandIconButton
                                key="justifyFull"
                                tooltip={tr.editingJustify()}
                                withoutShortcut
                                >{@html justifyFullIcon}</CommandIconButton
                            >
                        </ButtonGroupItem>
                    </ButtonGroup>
                </SectionItem>

                <SectionItem id="indentation">
                    <ButtonGroup>
                        <ButtonGroupItem>
                            <OnlyEditable let:disabled>
                                <IconButton
                                    on:click={outdentListItem}
                                    tooltip={tr.editingOutdent()}
                                    {disabled}
                                >
                                    {@html outdentIcon}
                                </IconButton>
                            </OnlyEditable>
                        </ButtonGroupItem>

                        <ButtonGroupItem>
                            <OnlyEditable let:disabled>
                                <IconButton
                                    on:click={indentListItem}
                                    tooltip={tr.editingIndent()}
                                    {disabled}
                                >
                                    {@html indentIcon}
                                </IconButton>
                            </OnlyEditable>
                        </ButtonGroupItem>
                    </ButtonGroup>
                </SectionItem>
            </ButtonDropdown>
        </WithDropdownMenu>
    </ButtonGroupItem>
</ButtonGroup>
