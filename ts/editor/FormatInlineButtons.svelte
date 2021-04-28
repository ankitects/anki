<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import * as tr from "lib/i18n";

    import CommandIconButton from "components/CommandIconButton.svelte";
    import IconButton from "components/IconButton.svelte";
    import ButtonGroup from "components/ButtonGroup.svelte";
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
        <CommandIconButton
            tooltip={`${tr.editingBoldText()} (${shortcutLabel})`}
            command="bold"
            onClick={() => document.execCommand('bold')}
            on:mount={createShortcut}>
            {@html boldIcon}
        </CommandIconButton>
    </WithShortcut>

    <WithShortcut shortcut="Control+KeyI">
        <CommandIconButton tooltip={tr.editingItalicText()} command="italic">
            {@html italicIcon}
        </CommandIconButton>
    </WithShortcut>

    <WithShortcut shortcut="Control+KeyU">
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
    </WithShortcut>
</ButtonGroup>
