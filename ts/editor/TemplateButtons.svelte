<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import * as tr from "lib/i18n";
    import { bridgeCommand } from "lib/bridgecommand";

    import ButtonGroup from "components/ButtonGroup.svelte";
    import ButtonGroupItem from "components/ButtonGroupItem.svelte";
    import IconButton from "components/IconButton.svelte";
    import DropdownMenu from "components/DropdownMenu.svelte";
    import DropdownItem from "components/DropdownItem.svelte";
    import WithDropdownMenu from "components/WithDropdownMenu.svelte";
    import WithShortcut from "components/WithShortcut.svelte";
    import ClozeButton from "./ClozeButton.svelte";

    import { wrap } from "./wrap";
    import { appendInParentheses } from "./helpers";
    import { paperclipIcon, micIcon, functionIcon, xmlIcon } from "./icons";

    export let api = {};

    function onAttachment(): void {
        bridgeCommand("attach");
    }

    function onRecord(): void {
        bridgeCommand("record");
    }

    function onHtmlEdit(): void {
        bridgeCommand("htmlEdit");
    }
</script>

<ButtonGroup {api}>
    <ButtonGroupItem>
        <WithShortcut shortcut={"F3"} let:createShortcut let:shortcutLabel>
            <IconButton
                tooltip={appendInParentheses(
                    tr.editingAttachPicturesaudiovideo(),
                    shortcutLabel
                )}
                iconSize={70}
                on:click={onAttachment}
                on:mount={createShortcut}
            >
                {@html paperclipIcon}
            </IconButton>
        </WithShortcut>
    </ButtonGroupItem>

    <ButtonGroupItem>
        <WithShortcut shortcut={"F5"} let:createShortcut let:shortcutLabel>
            <IconButton
                tooltip={appendInParentheses(tr.editingRecordAudio(), shortcutLabel)}
                iconSize={70}
                on:click={onRecord}
                on:mount={createShortcut}
            >
                {@html micIcon}
            </IconButton>
        </WithShortcut>
    </ButtonGroupItem>

    <ButtonGroupItem id="cloze">
        <ClozeButton />
    </ButtonGroupItem>

    <ButtonGroupItem>
        <WithDropdownMenu let:createDropdown let:menuId>
            <IconButton on:mount={createDropdown}>
                {@html functionIcon}
            </IconButton>

            <DropdownMenu id={menuId}>
                <WithShortcut
                    shortcut={"Control+M, M"}
                    let:createShortcut
                    let:shortcutLabel
                >
                    <DropdownItem
                        on:click={() => wrap("\\(", "\\)")}
                        on:mount={createShortcut}
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
                        on:click={() => wrap("\\[", "\\]")}
                        on:mount={createShortcut}
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
                        on:click={() => wrap("\\(\\ce{", "}\\)")}
                        on:mount={createShortcut}
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
                        on:click={() => wrap("[latex]", "[/latex]")}
                        on:mount={createShortcut}
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
                        on:click={() => wrap("[$]", "[/$]")}
                        on:mount={createShortcut}
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
                        on:click={() => wrap("[$$]", "[/$$]")}
                        on:mount={createShortcut}
                    >
                        {tr.editingLatexMathEnv()}
                        <span class="ps-1 float-end">{shortcutLabel}</span>
                    </DropdownItem>
                </WithShortcut>
            </DropdownMenu>
        </WithDropdownMenu>
    </ButtonGroupItem>

    <ButtonGroupItem>
        <WithShortcut shortcut={"Control+Shift+X"} let:createShortcut let:shortcutLabel>
            <IconButton
                tooltip={appendInParentheses(tr.editingHtmlEditor(), shortcutLabel)}
                iconSize={70}
                on:click={onHtmlEdit}
                on:mount={createShortcut}
            >
                {@html xmlIcon}
            </IconButton>
        </WithShortcut>
    </ButtonGroupItem>
</ButtonGroup>
