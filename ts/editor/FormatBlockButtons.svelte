<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import type { EditingArea } from "./editing-area";
    import * as tr from "lib/i18n";

    import ButtonGroup from "components/ButtonGroup.svelte";
    import ButtonGroupItem from "components/ButtonGroupItem.svelte";
    import IconButton from "components/IconButton.svelte";
    import ButtonDropdown from "components/ButtonDropdown.svelte";
    import Item from "components/Item.svelte";
    import WithDropdown from "components/WithDropdown.svelte";
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
        } else {
            alert("Indent/unindent currently only works with lists.");
        }
    }

    function indentListItem() {
        const currentField = document.activeElement as EditingArea;
        if (getListItem(currentField.shadowRoot!)) {
            document.execCommand("indent");
        } else {
            alert("Indent/unindent currently only works with lists.");
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
        <WithDropdown let:createDropdown>
            <OnlyEditable let:disabled>
                <IconButton
                    {disabled}
                    on:mount={(event) => createDropdown(event.detail.button)}
                >
                    {@html listOptionsIcon}
                </IconButton>
            </OnlyEditable>

            <ButtonDropdown>
                <Item id="justify">
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
                </Item>

                <Item id="indentation">
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
                </Item>
            </ButtonDropdown>
        </WithDropdown>
    </ButtonGroupItem>
</ButtonGroup>
