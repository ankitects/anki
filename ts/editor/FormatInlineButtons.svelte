<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import * as tr from "lib/i18n";

    import ButtonGroup from "components/ButtonGroup.svelte";
    import ButtonGroupItem from "components/ButtonGroupItem.svelte";
    import IconButton from "components/IconButton.svelte";
    import WithState from "components/WithState.svelte";
    import WithShortcut from "components/WithShortcut.svelte";

    import {
        boldIcon,
        italicIcon,
        underlineIcon,
        superscriptIcon,
        subscriptIcon,
        eraserIcon,
    } from "./icons";
    import { appendInParentheses } from "./helpers";

    export let api = {};
</script>

<ButtonGroup {api}>
    <ButtonGroupItem>
        <WithShortcut shortcut={"Control+B"} let:createShortcut let:shortcutLabel>
            <WithState
                key="bold"
                update={() => document.queryCommandState("bold")}
                let:state={active}
                let:updateState
            >
                <IconButton
                    tooltip={appendInParentheses(tr.editingBoldText(), shortcutLabel)}
                    {active}
                    on:click={(event) => {
                        document.execCommand("bold");
                        updateState(event);
                    }}
                    on:mount={createShortcut}
                >
                    {@html boldIcon}
                </IconButton>
            </WithState>
        </WithShortcut>
    </ButtonGroupItem>

    <ButtonGroupItem>
        <WithShortcut shortcut={"Control+I"} let:createShortcut let:shortcutLabel>
            <WithState
                key="italic"
                update={() => document.queryCommandState("italic")}
                let:state={active}
                let:updateState
            >
                <IconButton
                    tooltip={appendInParentheses(tr.editingItalicText(), shortcutLabel)}
                    {active}
                    on:click={(event) => {
                        document.execCommand("italic");
                        updateState(event);
                    }}
                    on:mount={createShortcut}
                >
                    {@html italicIcon}
                </IconButton>
            </WithState>
        </WithShortcut>
    </ButtonGroupItem>

    <ButtonGroupItem>
        <WithShortcut shortcut={"Control+U"} let:createShortcut let:shortcutLabel>
            <WithState
                key="underline"
                update={() => document.queryCommandState("underline")}
                let:state={active}
                let:updateState
            >
                <IconButton
                    tooltip={appendInParentheses(
                        tr.editingUnderlineText(),
                        shortcutLabel
                    )}
                    {active}
                    on:click={(event) => {
                        document.execCommand("underline");
                        updateState(event);
                    }}
                    on:mount={createShortcut}
                >
                    {@html underlineIcon}
                </IconButton>
            </WithState>
        </WithShortcut>
    </ButtonGroupItem>

    <ButtonGroupItem>
        <WithShortcut shortcut={"Control+="} let:createShortcut let:shortcutLabel>
            <WithState
                key="superscript"
                update={() => document.queryCommandState("superscript")}
                let:state={active}
                let:updateState
            >
                <IconButton
                    tooltip={appendInParentheses(
                        tr.editingSuperscript(),
                        shortcutLabel
                    )}
                    {active}
                    on:click={(event) => {
                        document.execCommand("superscript");
                        updateState(event);
                    }}
                    on:mount={createShortcut}
                >
                    {@html superscriptIcon}
                </IconButton>
            </WithState>
        </WithShortcut>
    </ButtonGroupItem>

    <ButtonGroupItem>
        <WithShortcut shortcut={"Control+Shift+="} let:createShortcut let:shortcutLabel>
            <WithState
                key="subscript"
                update={() => document.queryCommandState("subscript")}
                let:state={active}
                let:updateState
            >
                <IconButton
                    tooltip={appendInParentheses(tr.editingSubscript(), shortcutLabel)}
                    {active}
                    on:click={(event) => {
                        document.execCommand("subscript");
                        updateState(event);
                    }}
                    on:mount={createShortcut}
                >
                    {@html subscriptIcon}
                </IconButton>
            </WithState>
        </WithShortcut>
    </ButtonGroupItem>

    <ButtonGroupItem>
        <WithShortcut shortcut={"Control+R"} let:createShortcut let:shortcutLabel>
            <IconButton
                tooltip={appendInParentheses(
                    tr.editingRemoveFormatting(),
                    shortcutLabel
                )}
                on:click={() => {
                    document.execCommand("removeFormat");
                }}
                on:mount={createShortcut}
            >
                {@html eraserIcon}
            </IconButton>
        </WithShortcut>
    </ButtonGroupItem>
</ButtonGroup>
