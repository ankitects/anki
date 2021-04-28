<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import * as tr from "lib/i18n";
    import { bridgeCommand } from "lib/bridgecommand";

    import IconButton from "components/IconButton.svelte";
    import ButtonGroup from "components/ButtonGroup.svelte";
    import DropdownMenu from "components/DropdownMenu.svelte";
    import DropdownItem from "components/DropdownItem.svelte";
    import WithDropdownMenu from "components/WithDropdownMenu.svelte";
    import WithShortcut from "components/WithShortcut.svelte";
    import ClozeButton from "./ClozeButton.svelte";

    import { wrap } from "./wrap";
    import { paperclipIcon, micIcon, functionIcon, xmlIcon } from "./icons";

    function onAttachment(): void {
        bridgeCommand("attach");
    }

    function onRecord(): void {
        bridgeCommand("record");
    }

    function onHtmlEdit(): void {
        bridgeCommand("htmlEdit");
    }

    const mathjaxMenuId = "mathjaxMenu";
</script>

<ButtonGroup id="template">
    <WithShortcut shortcut="F3" let:createShortcut let:shortcutLabel>
        <IconButton
            tooltip={tr.editingAttachPicturesaudiovideo}
            on:click={onAttachment}>
            {@html paperclipIcon}
        </IconButton>
    </WithShortcut>

    <WithShortcut shortcut="F5" let:createShortcut let:shortcutLabel>
        <IconButton tooltip={tr.editingRecordAudio} on:click={onRecord}>
            {@html micIcon}
        </IconButton>
    </WithShortcut>

    <ClozeButton />

    <IconButton>
        {@html functionIcon}
    </IconButton>

    <WithShortcut shortcut="Control+Shift+KeyX" let:createShortcut let:shortcutLabel>
        <IconButton tooltip={tr.editingHtmlEditor} on:click={onHtmlEdit}>
            {@html xmlIcon}
        </IconButton>
    </WithShortcut>
</ButtonGroup>

<DropdownMenu id={matjaxMenuId}>
    <WithShortcut shortcut="Control+KeyM, KeyM" let:createShortcut let:shortcutLabel>
        <DropdownItem on:click={() => wrap('\\(', '\\)')}>
            {tr.editingMathjaxInline}
        </DropdownItem>
    </WithShortcut>

    <WithShortcut shortcut="Control+KeyM, KeyE" let:createShortcut let:shortcutLabel>
        <DropdownItem on:click={() => wrap('\\[', '\\]')}>
            {tr.editingMathjaxBlock}
        </DropdownItem>
    </WithShortcut>

    <WithShortcut shortcut="Control+KeyM, KeyC" let:createShortcut let:shortcutLabel>
        <DropdownItem on:click={() => wrap('\\(\\ce{', '}\\)')}>
            {tr.editingMathjaxChemistry}
        </DropdownItem>
    </WithShortcut>

    <WithShortcut shortcut="Control+KeyT, KeyT" let:createShortcut let:shortcutLabel>
        <DropdownItem on:click={() => wrap('[latex]', '[/latex]')}>
            {tr.editingLatex}
        </DropdownItem>
    </WithShortcut>

    <WithShortcut shortcut="Control+KeyT, KeyE" let:createShortcut let:shortcutLabel>
        <DropdownItem on:click={() => wrap('[$]', '[/$]')}>
            {tr.editingLatexEquation}
        </DropdownItem>
    </WithShortcut>

    <WithShortcut shortcut="Control+KeyT, KeyM" let:createShortcut let:shortcutLabel>
        <DropdownItem on:click={() => wrap('[$$]', '[/$$]')}>
            {tr.editingLatexMathEnv}
        </DropdownItem>
    </WithShortcut>
</DropdownMenu>
