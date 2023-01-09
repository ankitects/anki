<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { getListItem } from "@tslib/dom";
    import { preventDefault } from "@tslib/events";
    import * as tr from "@tslib/ftl";
    import { getPlatformString, registerShortcut } from "@tslib/shortcuts";
    import { onMount } from "svelte";

    import ButtonGroup from "../../components/ButtonGroup.svelte";
    import ButtonGroupItem, {
        createProps,
        setSlotHostContext,
        updatePropsList,
    } from "../../components/ButtonGroupItem.svelte";
    import ButtonToolbar from "../../components/ButtonToolbar.svelte";
    import DynamicallySlottable from "../../components/DynamicallySlottable.svelte";
    import IconButton from "../../components/IconButton.svelte";
    import Popover from "../../components/Popover.svelte";
    import WithFloating from "../../components/WithFloating.svelte";
    import { execCommand } from "../../domlib";
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

    onMount(() => {
        registerShortcut((event: KeyboardEvent) => {
            preventDefault(event);
            indentListItem();
        }, indentKeyCombination);
        registerShortcut((event: KeyboardEvent) => {
            preventDefault(event);
            outdentListItem();
        }, outdentKeyCombination);
    });

    const { focusedInput } = context.get();

    $: disabled = !$focusedInput || !editingInputIsRichText($focusedInput);

    let showFloating = false;
    $: if (disabled) {
        showFloating = false;
    }
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
            <WithFloating
                show={showFloating}
                inline
                on:close={() => (showFloating = false)}
                let:asReference
            >
                <span class="block-buttons" use:asReference>
                    <IconButton
                        {disabled}
                        on:click={() => (showFloating = !showFloating)}
                    >
                        {@html listOptionsIcon}
                    </IconButton>
                </span>

                <Popover slot="floating" --popover-padding-inline="0">
                    <ButtonToolbar wrap={false}>
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
                        </ButtonGroup>
                    </ButtonToolbar>
                </Popover>
            </WithFloating>
        </ButtonGroupItem>
    </DynamicallySlottable>
</ButtonGroup>

<style lang="scss">
    .block-buttons {
        line-height: 1;
    }
</style>
