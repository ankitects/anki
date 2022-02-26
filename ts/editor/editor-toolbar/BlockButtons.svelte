<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import ButtonDropdown from "../../components/ButtonDropdown.svelte";
    import ButtonGroup from "../../components/ButtonGroup.svelte";
    import ButtonGroupItem, {
        createProps,
        setSlotHostContext,
        updatePropsList,
    } from "../../components/ButtonGroupItem.svelte";
    import DynamicallySlottable from "../../components/DynamicallySlottable.svelte";
    import IconButton from "../../components/IconButton.svelte";
    import Item from "../../components/Item.svelte";
    import Shortcut from "../../components/Shortcut.svelte";
    import WithDropdown from "../../components/WithDropdown.svelte";
    import { getListItem } from "../../lib/dom";
    import * as tr from "../../lib/ftl";
    import { getPlatformString } from "../../lib/shortcuts";
    import { execCommand } from "../helpers";
    import { context } from "../NoteEditor.svelte";
    import { editingInputIsRichText } from "../rich-text-input";
    import CommandIconButton from "./CommandIconButton.svelte";
    import {
        indentIcon,
        justifyCenterIcon,
        justifyFullIcon,
        justifyLeftIcon,
        justifyRightIcon,
        listOptionsIcon,
        olIcon,
        outdentIcon,
        ulIcon,
    } from "./icons";

    export let api = {};

    const outdentKeyCombination = "Control+Shift+,";
    function outdentListItem() {
        if (getListItem(document.activeElement!.shadowRoot!)) {
            execCommand("outdent");
        } else {
            alert("Indent/unindent currently only works with lists.");
        }
    }

    const indentKeyCombination = "Control+Shift+.";
    function indentListItem() {
        if (getListItem(document.activeElement!.shadowRoot!)) {
            execCommand("indent");
        } else {
            alert("Indent/unindent currently only works with lists.");
        }
    }

    const { focusedInput } = context.get();
    $: disabled = !editingInputIsRichText($focusedInput);
</script>

<ButtonGroup>
    <DynamicallySlottable
        slotHost={ButtonGroupItem}
        {createProps}
        {updatePropsList}
        {setSlotHostContext}
        {api}
    >
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
                            <CommandIconButton
                                key="justifyLeft"
                                tooltip={tr.editingAlignLeft()}
                                --border-left-radius="5px"
                                --border-right-radius="0px"
                                >{@html justifyLeftIcon}</CommandIconButton
                            >

                            <CommandIconButton
                                key="justifyCenter"
                                tooltip={tr.editingCenter()}
                                >{@html justifyCenterIcon}</CommandIconButton
                            >

                            <CommandIconButton
                                key="justifyRight"
                                tooltip={tr.editingAlignRight()}
                                >{@html justifyRightIcon}</CommandIconButton
                            >

                            <CommandIconButton
                                key="justifyFull"
                                tooltip={tr.editingJustify()}
                                --border-right-radius="5px"
                                >{@html justifyFullIcon}</CommandIconButton
                            >
                        </ButtonGroup>
                    </Item>

                    <Item id="indentation">
                        <ButtonGroup>
                            <IconButton
                                tooltip="{tr.editingOutdent()} ({getPlatformString(
                                    outdentKeyCombination,
                                )})"
                                {disabled}
                                on:click={outdentListItem}
                                --border-left-radius="5px"
                                --border-right-radius="0px"
                            >
                                {@html outdentIcon}
                            </IconButton>

                            <Shortcut
                                keyCombination={outdentKeyCombination}
                                on:action={outdentListItem}
                            />

                            <IconButton
                                tooltip="{tr.editingIndent()} ({getPlatformString(
                                    indentKeyCombination,
                                )})"
                                {disabled}
                                on:click={indentListItem}
                                --border-right-radius="5px"
                            >
                                {@html indentIcon}
                            </IconButton>

                            <Shortcut
                                keyCombination={indentKeyCombination}
                                on:action={indentListItem}
                            />
                        </ButtonGroup>
                    </Item>
                </ButtonDropdown>
            </WithDropdown>
        </ButtonGroupItem>
    </DynamicallySlottable>
</ButtonGroup>
