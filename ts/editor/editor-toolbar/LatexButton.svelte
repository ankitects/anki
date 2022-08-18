<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { writable } from "svelte/store";

    import DropdownItem from "../../components/DropdownItem.svelte";
    import IconButton from "../../components/IconButton.svelte";
    import Popover from "../../components/Popover.svelte";
    import Shortcut from "../../components/Shortcut.svelte";
    import WithFloating from "../../components/WithFloating.svelte";
    import { mathjaxConfig } from "../../editable/mathjax-element";
    import { bridgeCommand } from "../../lib/bridgecommand";
    import * as tr from "../../lib/ftl";
    import { getPlatformString } from "../../lib/shortcuts";
    import { wrapInternal } from "../../lib/wrap";
    import { context as noteEditorContext } from "../NoteEditor.svelte";
    import type { RichTextInputAPI } from "../rich-text-input";
    import { editingInputIsRichText } from "../rich-text-input";
    import { functionIcon } from "./icons";

    const { focusedInput } = noteEditorContext.get();
    $: richTextAPI = $focusedInput as RichTextInputAPI;

    async function surround(front: string, back: string): Promise<void> {
        const element = await richTextAPI.element;
        wrapInternal(element, front, back, false);
    }

    function onMathjaxInline(): void {
        surround("<anki-mathjax focusonmount>", "</anki-mathjax>");
    }

    function onMathjaxBlock(): void {
        surround('<anki-mathjax block="true" focusonmount>', "</anki-matjax>");
    }

    function onMathjaxChemistry(): void {
        surround('<anki-mathjax focusonmount="0,4">\\ce{', "}</anki-mathjax>");
    }

    function onLatex(): void {
        surround("[latex]", "[/latex]");
    }

    function onLatexEquation(): void {
        surround("[$]", "[/$]");
    }

    function onLatexMathEnv(): void {
        surround("[$$]", "[/$$]");
    }

    function toggleShowMathjax(): void {
        mathjaxConfig.enabled = !mathjaxConfig.enabled;
        bridgeCommand("toggleMathjax");
    }

    type LatexItem = [() => void, string, string];

    const dropdownItems: LatexItem[] = [
        [onMathjaxInline, "Control+M, M", tr.editingMathjaxInline()],
        [onMathjaxBlock, "Control+M, E", tr.editingMathjaxBlock()],
        [onMathjaxChemistry, "Control+M, C", tr.editingMathjaxChemistry()],
        [onLatex, "Control+T, T", tr.editingLatex()],
        [onLatexEquation, "Control+T, E", tr.editingLatexEquation()],
        [onLatexMathEnv, "Control+T, M", tr.editingLatexMathEnv()],
    ];

    $: disabled = !editingInputIsRichText($focusedInput);

    const showDropdown = writable(false);

    $: if (disabled) {
        $showDropdown = false;
    }
</script>

<WithFloating show={showDropdown} closeOnInsideClick>
    <span
        class="latex-button"
        slot="reference"
        let:asReference
        use:asReference
        let:toggle
    >
        <IconButton slot="reference" {disabled} on:click={toggle}>
            {@html functionIcon}
        </IconButton>
    </span>

    <Popover slot="floating">
        {#each dropdownItems as [callback, keyCombination, label]}
            <DropdownItem on:click={callback}>
                {label}
                <span class="ms-auto ps-2 shortcut"
                    >{getPlatformString(keyCombination)}</span
                >
            </DropdownItem>
            <Shortcut {keyCombination} on:action={callback} />
        {/each}
        <DropdownItem on:click={toggleShowMathjax}>
            <span>{tr.editingToggleMathjaxRendering()}</span>
        </DropdownItem>
    </Popover>
</WithFloating>

<style lang="scss">
    .latex-button {
        line-height: 1;
    }

    .shortcut {
        font: Verdana;
    }
</style>
