<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import ButtonGroup from "../../components/ButtonGroup.svelte";
    import ButtonGroupItem from "../../components/ButtonGroupItem.svelte";
    import IconButton from "../../components/IconButton.svelte";
    import ButtonDropdown from "../../components/ButtonDropdown.svelte";
    import Item from "../../components/Item.svelte";
    import WithShortcut from "../../components/WithShortcut.svelte";
    import WithDropdown from "../../components/WithDropdown.svelte";
    import CommandIconButton from "./CommandIconButton.svelte";

    import * as tr from "../../lib/ftl";
    import { getListItem } from "../../lib/dom";
    import { withButton } from "../../components/helpers";
    import { execCommand } from "../helpers";
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
    import { getNoteEditor } from "../OldEditorAdapter.svelte";

    export let api = {};

    function outdentListItem() {
        if (getListItem(document.activeElement!.shadowRoot!)) {
            execCommand("outdent");
        } else {
            alert("Indent/unindent currently only works with lists.");
        }
    }

    function indentListItem() {
        if (getListItem(document.activeElement!.shadowRoot!)) {
            execCommand("indent");
        } else {
            alert("Indent/unindent currently only works with lists.");
        }
    }

    const { focusInRichText } = getNoteEditor();
    $: disabled = !$focusInRichText;
</script>

<ButtonGroup {api}>
    <ButtonGroupItem>
        <CommandIconButton
            key="insertUnorderedList"
            tooltip={tr.editingUnorderedList()}
            shortcut="Control+,">{@html ulIcon}</CommandIconButton
        >
    </ButtonGroupItem>

    <ButtonGroupItem>
        <CommandIconButton
            key="insertOrderedList"
            tooltip={tr.editingOrderedList()}
            shortcut="Control+.">{@html olIcon}</CommandIconButton
        >
    </ButtonGroupItem>

    <ButtonGroupItem>
        <WithDropdown let:createDropdown>
            <IconButton
                {disabled}
                on:mount={(event) => createDropdown(event.detail.button)}
            >
                {@html listOptionsIcon}
            </IconButton>

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
                            <WithShortcut
                                shortcut="Control+Shift+,"
                                let:createShortcut
                                let:shortcutLabel
                            >
                                <IconButton
                                    on:click={outdentListItem}
                                    on:mount={withButton(createShortcut)}
                                    tooltip="{tr.editingOutdent()} ({shortcutLabel})"
                                    {disabled}
                                >
                                    {@html outdentIcon}
                                </IconButton>
                            </WithShortcut>
                        </ButtonGroupItem>

                        <ButtonGroupItem>
                            <WithShortcut
                                shortcut="Control+Shift+."
                                let:createShortcut
                                let:shortcutLabel
                            >
                                <IconButton
                                    on:click={indentListItem}
                                    on:mount={withButton(createShortcut)}
                                    tooltip="{tr.editingIndent()} ({shortcutLabel})"
                                    {disabled}
                                >
                                    {@html indentIcon}
                                </IconButton>
                            </WithShortcut>
                        </ButtonGroupItem>
                    </ButtonGroup>
                </Item>
            </ButtonDropdown>
        </WithDropdown>
    </ButtonGroupItem>
</ButtonGroup>
