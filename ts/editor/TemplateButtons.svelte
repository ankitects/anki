<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import * as tr from "lib/i18n";
    import { bridgeCommand } from "lib/bridgecommand";
    import { fieldFocusedKey, inCodableKey } from "./context-keys";

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

    import { getCurrentField } from ".";
    import { appendInParentheses } from "./helpers";
    import { wrapCurrent } from "./wrap";
    import { paperclipIcon, micIcon, functionIcon, xmlIcon } from "./icons";

    export let api = {};

    function onAttachment(): void {
        bridgeCommand("attach");
    }

    function onRecord(): void {
        bridgeCommand("record");
    }

    function onHtmlEdit() {
        const currentField = getCurrentField();
        if (currentField) {
            currentField.toggleHtmlEdit();
        }
    }
</script>

<ButtonGroup {api}>
    <ButtonGroupItem>
        <WithShortcut shortcut={"F3"} let:createShortcut let:shortcutLabel>
            <OnlyEditable let:disabled>
                <IconButton
                    tooltip={appendInParentheses(
                        tr.editingAttachPicturesaudiovideo(),
                        shortcutLabel
                    )}
                    iconSize={70}
                    {disabled}
                    on:click={onAttachment}
                    on:mount={(event) => createShortcut(event.detail.button)}
                >
                    {@html paperclipIcon}
                </IconButton>
            </OnlyEditable>
        </WithShortcut>
    </ButtonGroupItem>

    <ButtonGroupItem>
        <WithShortcut shortcut={"F5"} let:createShortcut let:shortcutLabel>
            <OnlyEditable let:disabled>
                <IconButton
                    tooltip={appendInParentheses(
                        tr.editingRecordAudio(),
                        shortcutLabel
                    )}
                    iconSize={70}
                    {disabled}
                    on:click={onRecord}
                    on:mount={(event) => createShortcut(event.detail.button)}
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
                <IconButton
                    {disabled}
                    on:mount={(event) => createDropdown(event.detail.button)}
                >
                    {@html functionIcon}
                </IconButton>
            </OnlyEditable>

            <DropdownMenu>
                <WithShortcut
                    shortcut={"Control+M, M"}
                    let:createShortcut
                    let:shortcutLabel
                >
                    <DropdownItem
                        on:click={() => wrapCurrent("\\(", "\\)")}
                        on:mount={(event) => createShortcut(event.detail.button)}
                    >
                        {tr.editingMathjaxInline()}
                        <span class="ps-1 float-end">{shortcutLabel}</span>
                    </DropdownItem>
                </WithShortcut>

                <WithShortcut
                    shortcut={"Control+M, E"}
                    let:createShortcut
                    let:shortcutLabel
                >
                    <DropdownItem
                        on:click={() => wrapCurrent("\\[", "\\]")}
                        on:mount={(event) => createShortcut(event.detail.button)}
                    >
                        {tr.editingMathjaxBlock()}
                        <span class="ps-1 float-end">{shortcutLabel}</span>
                    </DropdownItem>
                </WithShortcut>

                <WithShortcut
                    shortcut={"Control+M, C"}
                    let:createShortcut
                    let:shortcutLabel
                >
                    <DropdownItem
                        on:click={() => wrapCurrent("\\(\\ce{", "}\\)")}
                        on:mount={(event) => createShortcut(event.detail.button)}
                    >
                        {tr.editingMathjaxChemistry()}
                        <span class="ps-1 float-end">{shortcutLabel}</span>
                    </DropdownItem>
                </WithShortcut>

                <WithShortcut
                    shortcut={"Control+T, T"}
                    let:createShortcut
                    let:shortcutLabel
                >
                    <DropdownItem
                        on:click={() => wrapCurrent("[latex]", "[/latex]")}
                        on:mount={(event) => createShortcut(event.detail.button)}
                    >
                        {tr.editingLatex()}
                        <span class="ps-1 float-end">{shortcutLabel}</span>
                    </DropdownItem>
                </WithShortcut>

                <WithShortcut
                    shortcut={"Control+T, E"}
                    let:createShortcut
                    let:shortcutLabel
                >
                    <DropdownItem
                        on:click={() => wrapCurrent("[$]", "[/$]")}
                        on:mount={(event) => createShortcut(event.detail.button)}
                    >
                        {tr.editingLatexEquation()}
                        <span class="ps-1 float-end">{shortcutLabel}</span>
                    </DropdownItem>
                </WithShortcut>

                <WithShortcut
                    shortcut={"Control+T, M"}
                    let:createShortcut
                    let:shortcutLabel
                >
                    <DropdownItem
                        on:click={() => wrapCurrent("[$$]", "[/$$]")}
                        on:mount={(event) => createShortcut(event.detail.button)}
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
            <WithContext key={inCodableKey} let:context={inCodable}>
                <WithShortcut
                    shortcut={"Control+Shift+X"}
                    let:createShortcut
                    let:shortcutLabel
                >
                    <IconButton
                        tooltip={appendInParentheses(
                            tr.editingHtmlEditor(),
                            shortcutLabel
                        )}
                        iconSize={70}
                        active={inCodable}
                        disabled={!fieldFocused}
                        on:click={onHtmlEdit}
                        on:mount={(event) => createShortcut(event.detail.button)}
                    >
                        {@html xmlIcon}
                    </IconButton>
                </WithShortcut>
            </WithContext>
        </WithContext>
    </ButtonGroupItem>
</ButtonGroup>
