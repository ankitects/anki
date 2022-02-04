<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import DropdownItem from "../../components/DropdownItem.svelte";
    import DropdownMenu from "../../components/DropdownMenu.svelte";
    import { withButton } from "../../components/helpers";
    import IconButton from "../../components/IconButton.svelte";
    import Shortcut from "../../components/Shortcut.svelte";
    import WithDropdown from "../../components/WithDropdown.svelte";
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
        surround("<anki-mathjax focusonmount>\\ce{", "}</anki-mathjax>");
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
</script>

<WithDropdown let:createDropdown>
    <IconButton {disabled} on:mount={withButton(createDropdown)}>
        {@html functionIcon}
    </IconButton>

    <DropdownMenu>
        {#each dropdownItems as [callback, keyCombination, label]}
            <DropdownItem on:click={callback}>
                {label}
                <span class="ps-1 float-end">{getPlatformString(keyCombination)}</span>
            </DropdownItem>
            <Shortcut {keyCombination} on:action={callback} />
        {/each}
    </DropdownMenu>
</WithDropdown>
