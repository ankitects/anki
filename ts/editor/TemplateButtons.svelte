<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import * as tr from "lib/i18n";
    import { bridgeCommand } from "lib/bridgecommand";
    import {
        fieldFocusedKey,
        currentFieldKey,
        focusInCodableKey,
    } from "lib/context-keys";

    import ButtonGroup from "components/ButtonGroup.svelte";
    import ButtonGroupItem from "components/ButtonGroupItem.svelte";
    import IconButton from "components/IconButton.svelte";
    import DropdownMenu from "components/DropdownMenu.svelte";
    import DropdownItem from "components/DropdownItem.svelte";
    import WithDropdown from "components/WithDropdown.svelte";
    import WithShortcut from "components/WithShortcut.svelte";
    import WithContext from "components/WithContext.svelte";
    import OnlyEditable from "./OnlyEditable.svelte";
    import ClozeButton from "./ClozeButton.svelte";

    import type { Writable } from "svelte/store";
    import { getContext } from "svelte";
    import type { EditorFieldAPI } from "./MultiRootEditor.svelte";
    import { appendInParentheses } from "./helpers";
    import { withButton } from "components/helpers";
    import { wrapCurrent } from "./wrap";
    import { paperclipIcon, micIcon, functionIcon, xmlIcon } from "./icons";

    export let api = {};

    function onAttachment(): void {
        bridgeCommand("attach");
    }

    function onRecord(): void {
        bridgeCommand("record");
    }

    const currentField = getContext<Writable<EditorFieldAPI | null>>(currentFieldKey);

    function onHtmlEdit() {
        $currentField?.editingArea!.toggleCodable();
    }
</script>

<ButtonGroup {api}>
    <ButtonGroupItem>
        <WithShortcut shortcut="F3" let:createShortcut let:shortcutLabel>
            <OnlyEditable let:disabled>
                <IconButton
                    tooltip={appendInParentheses(
                        tr.editingAttachPicturesaudiovideo(),
                        shortcutLabel
                    )}
                    iconSize={70}
                    {disabled}
                    on:click={onAttachment}
                    on:mount={withButton(createShortcut)}
                >
                    {@html paperclipIcon}
                </IconButton>
            </OnlyEditable>
        </WithShortcut>
    </ButtonGroupItem>

    <ButtonGroupItem>
        <WithShortcut shortcut="F5" let:createShortcut let:shortcutLabel>
            <OnlyEditable let:disabled>
                <IconButton
                    tooltip={appendInParentheses(
                        tr.editingRecordAudio(),
                        shortcutLabel
                    )}
                    iconSize={70}
                    {disabled}
                    on:click={onRecord}
                    on:mount={withButton(createShortcut)}
                >
                    {@html micIcon}
                </IconButton>
            </OnlyEditable>
        </WithShortcut>
    </ButtonGroupItem>

    <ButtonGroupItem id="cloze">
        <OnlyEditable>
            <ClozeButton />
        </OnlyEditable>
    </ButtonGroupItem>

    <ButtonGroupItem>
        <WithDropdown let:createDropdown>
            <OnlyEditable let:disabled>
                <IconButton {disabled} on:mount={withButton(createDropdown)}>
                    {@html functionIcon}
                </IconButton>
            </OnlyEditable>

            <DropdownMenu>
                <WithShortcut
                    shortcut="Control+M, M"
                    let:createShortcut
                    let:shortcutLabel
                >
                    <DropdownItem
                        on:click={() =>
                            wrapCurrent(
                                "<anki-mathjax focusonmount>",
                                "</anki-mathjax>"
                            )}
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
                            wrapCurrent(
                                '<anki-mathjax block="true" focusonmount>',
                                "</anki-matjax>"
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
                            wrapCurrent(
                                "<anki-mathjax focusonmount>\\ce{",
                                "}</anki-mathjax>"
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
                        on:click={() => wrapCurrent("[latex]", "[/latex]")}
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
                        on:click={() => wrapCurrent("[$]", "[/$]")}
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
                        on:click={() => wrapCurrent("[$$]", "[/$$]")}
                        on:mount={withButton(createShortcut)}
                    >
                        {tr.editingLatexMathEnv()}
                        <span class="ps-1 float-end">{shortcutLabel}</span>
                    </DropdownItem>
                </WithShortcut>
            </DropdownMenu>
        </WithDropdown>
    </ButtonGroupItem>

    <ButtonGroupItem>
        <WithContext key={fieldFocusedKey} let:context={fieldFocused}>
            <WithContext key={focusInCodableKey} let:context={focusInCodable}>
                <WithShortcut
                    shortcut="Control+Shift+X"
                    let:createShortcut
                    let:shortcutLabel
                >
                    <IconButton
                        tooltip={appendInParentheses(
                            tr.editingHtmlEditor(),
                            shortcutLabel
                        )}
                        iconSize={70}
                        active={focusInCodable}
                        disabled={!fieldFocused}
                        on:click={onHtmlEdit}
                        on:mount={withButton(createShortcut)}
                    >
                        {@html xmlIcon}
                    </IconButton>
                </WithShortcut>
            </WithContext>
        </WithContext>
    </ButtonGroupItem>
</ButtonGroup>
