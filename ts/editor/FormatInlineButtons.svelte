<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import * as tr from "lib/i18n";

    import IconButton from "components/IconButton.svelte";
    import ButtonGroup from "components/ButtonGroup.svelte";
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

    export let api = {};
</script>

<ButtonGroup id="notetype" {api}>
    <WithShortcut shortcut="Control+KeyB" let:createShortcut let:shortcutLabel>
        <WithState
            key="bold"
            update={() => document.queryCommandState('bold')}
            let:state={active}
            let:updateState>
            <IconButton
                tooltip={`${tr.editingBoldText()} (${shortcutLabel})`}
                {active}
                on:click={(event) => {
                    document.execCommand('bold');
                    updateState(event);
                }}
                on:mount={createShortcut}>
                {@html boldIcon}
            </IconButton>
        </WithState>
    </WithShortcut>

    <WithShortcut shortcut="Control+KeyI" let:createShortcut let:shortcutLabel>
        <WithState
            key="italic"
            update={() => document.queryCommandState('italic')}
            let:state={active}
            let:updateState>
            <IconButton
                tooltip={`${tr.editingItalicText()} (${shortcutLabel})`}
                {active}
                on:click={(event) => {
                    document.execCommand('italic');
                    updateState(event);
                }}
                on:mount={createShortcut}>
                {@html italicIcon}
            </IconButton>
        </WithState>
    </WithShortcut>

    <WithShortcut shortcut="Control+KeyU" let:createShortcut let:shortcutLabel>
        <WithState
            key="underline"
            update={() => document.queryCommandState('underline')}
            let:state={active}
            let:updateState>
            <IconButton
                tooltip={`${tr.editingUnderlineText()} (${shortcutLabel})`}
                {active}
                on:click={(event) => {
                    document.execCommand('underline');
                    updateState(event);
                }}
                on:mount={createShortcut}>
                {@html underlineIcon}
            </IconButton>
        </WithState>
    </WithShortcut>

    <!--<WithShortcut shortcut="Control+KeyU">
        <CommandIconButton tooltip={tr.editingUnderlineText()} command="underline">
            {@html underlineIcon}
        </CommandIconButton>
    </WithShortcut>

    <WithShortcut shortcut="Control+Shift+Equal">
        <CommandIconButton tooltip={tr.editingSuperscript()} command="superscript">
            {@html superscriptIcon}
        </CommandIconButton>
    </WithShortcut>

    <WithShortcut shortcut="Control+Equal">
        <CommandIconButton tooltip={tr.editingSubscript()} command="subscript">
            {@html subscriptIcon}
        </CommandIconButton>
    </WithShortcut>

    <WithShortcut shortcut="Control+KeyR">
        <IconButton
            tooltip={tr.editingSubscript()}
            on:click={() => {
                document.execCommand('removeFormat');
            }}>
            {@html eraserIcon}
        </IconButton>
    </WithShortcut> -->
</ButtonGroup>
