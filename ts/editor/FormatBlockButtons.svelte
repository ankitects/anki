<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import ButtonGroup from "../components/ButtonGroup.svelte";
    import ButtonGroupItem from "../components/ButtonGroupItem.svelte";
    import IconButton from "../components/IconButton.svelte";
    import ButtonDropdown from "../components/ButtonDropdown.svelte";
    import Item from "../components/Item.svelte";
    import WithDropdown from "../components/WithDropdown.svelte";
    import CommandIconButton from "./CommandIconButton.svelte";

    import * as tr from "../lib/ftl";
    import { getListItem } from "../lib/dom";
    import { execCommand } from "./helpers";
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
    import { getNoteEditor } from "./OldEditorAdapter.svelte";

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
                                on:click={outdentListItem}
                                tooltip={tr.editingOutdent()}
                                {disabled}
                            >
                                {@html outdentIcon}
                            </IconButton>
                        </ButtonGroupItem>

                        <ButtonGroupItem>
                            <IconButton
                                on:click={indentListItem}
                                tooltip={tr.editingIndent()}
                                {disabled}
                            >
                                {@html indentIcon}
                            </IconButton>
                        </ButtonGroupItem>
                    </ButtonGroup>
                </Item>
            </ButtonDropdown>
        </WithDropdown>
    </ButtonGroupItem>
</ButtonGroup>
