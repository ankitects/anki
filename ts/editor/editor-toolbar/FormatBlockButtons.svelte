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
    import Shortcut from "../../components/Shortcut.svelte";
    import WithDropdown from "../../components/WithDropdown.svelte";
    import CommandIconButton from "./CommandIconButton.svelte";

    import * as tr from "../../lib/ftl";
    import { getListItem } from "../../lib/dom";
    import { getPlatformString } from "../../lib/shortcuts";
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
                            <IconButton
                                tooltip="{tr.editingOutdent()} ({getPlatformString(
                                    outdentKeyCombination,
                                )})"
                                {disabled}
                                on:click={outdentListItem}
                            >
                                {@html outdentIcon}
                            </IconButton>

                            <Shortcut
                                keyCombination={outdentKeyCombination}
                                on:action={outdentListItem}
                            />
                        </ButtonGroupItem>

                        <ButtonGroupItem>
                            <IconButton
                                tooltip="{tr.editingIndent()} ({getPlatformString(
                                    indentKeyCombination,
                                )})"
                                {disabled}
                                on:click={indentListItem}
                            >
                                {@html indentIcon}
                            </IconButton>

                            <Shortcut
                                keyCombination={indentKeyCombination}
                                on:action={indentListItem}
                            />
                        </ButtonGroupItem>
                    </ButtonGroup>
                </Item>
            </ButtonDropdown>
        </WithDropdown>
    </ButtonGroupItem>
</ButtonGroup>
