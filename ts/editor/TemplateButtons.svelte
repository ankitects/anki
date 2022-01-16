<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import ButtonGroup from "../components/ButtonGroup.svelte";
    import ButtonGroupItem from "../components/ButtonGroupItem.svelte";
    import IconButton from "../components/IconButton.svelte";
    import DropdownMenu from "../components/DropdownMenu.svelte";
    import DropdownItem from "../components/DropdownItem.svelte";
    import WithDropdown from "../components/WithDropdown.svelte";
    import WithShortcut from "../components/WithShortcut.svelte";
    import ClozeButton from "./ClozeButton.svelte";

    import * as tr from "../lib/ftl";
    import { bridgeCommand } from "../lib/bridgecommand";
    import { wrapInternal } from "../lib/wrap";
    import { getNoteEditor } from "./OldEditorAdapter.svelte";
    import { withButton } from "../components/helpers";
    import { paperclipIcon, micIcon, functionIcon } from "./icons";
    import type { RichTextInputAPI } from "./RichTextInput.svelte";

    export let api = {};
    const { focusInRichText, activeInput } = getNoteEditor();

    function onAttachment(): void {
        bridgeCommand("attach");
    }

    function onRecord(): void {
        bridgeCommand("record");
    }

    $: richTextAPI = $activeInput as RichTextInputAPI;
    $: disabled = !$focusInRichText;

    async function surround(front: string, back: string): Promise<void> {
        const element = await richTextAPI.element;
        wrapInternal(element, front, back, false);
    }
</script>

<ButtonGroup {api}>
    <ButtonGroupItem>
        <WithShortcut shortcut="F3" let:createShortcut let:shortcutLabel>
            <IconButton
                tooltip="{tr.editingAttachPicturesaudiovideo} ({shortcutLabel})"
                iconSize={70}
                {disabled}
                on:click={onAttachment}
                on:mount={withButton(createShortcut)}
            >
                {@html paperclipIcon}
            </IconButton>
        </WithShortcut>
    </ButtonGroupItem>

    <ButtonGroupItem>
        <WithShortcut shortcut="F5" let:createShortcut let:shortcutLabel>
            <IconButton
                tooltip="{tr.editingRecordAudio()} ({shortcutLabel})"
                iconSize={70}
                {disabled}
                on:click={onRecord}
                on:mount={withButton(createShortcut)}
            >
                {@html micIcon}
            </IconButton>
        </WithShortcut>
    </ButtonGroupItem>

    <ButtonGroupItem id="cloze">
        <ClozeButton />
    </ButtonGroupItem>

    <ButtonGroupItem>
        <WithDropdown let:createDropdown>
            <IconButton {disabled} on:mount={withButton(createDropdown)}>
                {@html functionIcon}
            </IconButton>

            <DropdownMenu>
                <WithShortcut
                    shortcut="Control+M, M"
                    let:createShortcut
                    let:shortcutLabel
                >
                    <DropdownItem
                        on:click={() =>
                            surround("<anki-mathjax focusonmount>", "</anki-mathjax>")}
                        on:mount={withButton(createShortcut)}
                    >
                        {tr.editingMathjaxInline()}
                        <span class="ps-1 float-end">{shortcutLabel}</span>
                    </DropdownItem>
                </WithShortcut>

                <WithShortcut
                    shortcut="Control+M, E"
                    let:createShortcut
                    let:shortcutLabel
                >
                    <DropdownItem
                        on:click={() =>
                            surround(
                                '<anki-mathjax block="true" focusonmount>',
                                "</anki-matjax>",
                            )}
                        on:mount={withButton(createShortcut)}
                    >
                        {tr.editingMathjaxBlock()}
                        <span class="ps-1 float-end">{shortcutLabel}</span>
                    </DropdownItem>
                </WithShortcut>

                <WithShortcut
                    shortcut="Control+M, C"
                    let:createShortcut
                    let:shortcutLabel
                >
                    <DropdownItem
                        on:click={() =>
                            surround(
                                "<anki-mathjax focusonmount>\\ce{",
                                "}</anki-mathjax>",
                            )}
                        on:mount={withButton(createShortcut)}
                    >
                        {tr.editingMathjaxChemistry()}
                        <span class="ps-1 float-end">{shortcutLabel}</span>
                    </DropdownItem>
                </WithShortcut>

                <WithShortcut
                    shortcut="Control+T, T"
                    let:createShortcut
                    let:shortcutLabel
                >
                    <DropdownItem
                        on:click={() => surround("[latex]", "[/latex]")}
                        on:mount={withButton(createShortcut)}
                    >
                        {tr.editingLatex()}
                        <span class="ps-1 float-end">{shortcutLabel}</span>
                    </DropdownItem>
                </WithShortcut>

                <WithShortcut
                    shortcut="Control+T, E"
                    let:createShortcut
                    let:shortcutLabel
                >
                    <DropdownItem
                        on:click={() => surround("[$]", "[/$]")}
                        on:mount={withButton(createShortcut)}
                    >
                        {tr.editingLatexEquation()}
                        <span class="ps-1 float-end">{shortcutLabel}</span>
                    </DropdownItem>
                </WithShortcut>

                <WithShortcut
                    shortcut="Control+T, M"
                    let:createShortcut
                    let:shortcutLabel
                >
                    <DropdownItem
                        on:click={() => surround("[$$]", "[/$$]")}
                        on:mount={withButton(createShortcut)}
                    >
                        {tr.editingLatexMathEnv()}
                        <span class="ps-1 float-end">{shortcutLabel}</span>
                    </DropdownItem>
                </WithShortcut>
            </DropdownMenu>
        </WithDropdown>
    </ButtonGroupItem>
</ButtonGroup>
