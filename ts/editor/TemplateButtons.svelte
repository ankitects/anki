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

<ButtonGroup id="template" {api}>
    <ButtonGroupItem>
        <WithShortcut shortcut="F3" let:createShortcut let:shortcutLabel>
            <IconButton
                tooltip={appendInParentheses(tr.editingAttachPicturesaudiovideo(), shortcutLabel)}
                on:click={onAttachment}
                on:mount={createShortcut}>
                {@html paperclipIcon}
            </IconButton>
        </WithShortcut>
    </ButtonGroupItem>

    <ButtonGroupItem>
        <WithShortcut shortcut="F5" let:createShortcut let:shortcutLabel>
            <IconButton
                tooltip={appendInParentheses(tr.editingRecordAudio(), shortcutLabel)}
                on:click={onRecord}
                on:mount={createShortcut}>
                {@html micIcon}
            </IconButton>
        </WithShortcut>
    </ButtonGroupItem>

    <ButtonGroupItem id="cloze">
        <ClozeButton />
    </ButtonGroupItem>

    <WithDropdownMenu let:menuId let:createDropdown>
        <ButtonGroupItem>
            <IconButton on:mount={createDropdown}>
                {@html functionIcon}
            </IconButton>
        </ButtonGroupItem>

        <DropdownMenu id={menuId}>
            <WithShortcut
                shortcut="Control+KeyM, KeyM"
                let:createShortcut
                let:shortcutLabel>
                <DropdownItem
                    on:click={() => wrap('\\(', '\\)')}
                    on:mount={createShortcut}>
                    {tr.editingMathjaxInline()}
                    <span class="ps-1 float-end">{shortcutLabel}</span>
                </DropdownItem>
            </WithShortcut>

            <WithShortcut
                shortcut="Control+KeyM, KeyE"
                let:createShortcut
                let:shortcutLabel>
                <DropdownItem
                    on:click={() => wrap('\\[', '\\]')}
                    on:mount={createShortcut}>
                    {tr.editingMathjaxBlock()}
                    <span class="ps-1 float-end">{shortcutLabel}</span>
                </DropdownItem>
            </WithShortcut>

            <WithShortcut
                shortcut="Control+KeyM, KeyC"
                let:createShortcut
                let:shortcutLabel>
                <DropdownItem
                    on:click={() => wrap('\\(\\ce{', '}\\)')}
                    on:mount={createShortcut}>
                    {tr.editingMathjaxChemistry()}
                    <span class="ps-1 float-end">{shortcutLabel}</span>
                </DropdownItem>
            </WithShortcut>

            <WithShortcut
                shortcut="Control+KeyT, KeyT"
                let:createShortcut
                let:shortcutLabel>
                <DropdownItem
                    on:click={() => wrap('[latex]', '[/latex]')}
                    on:mount={createShortcut}>
                    {tr.editingLatex()}
                    <span class="ps-1 float-end">{shortcutLabel}</span>
                </DropdownItem>
            </WithShortcut>

            <WithShortcut
                shortcut="Control+KeyT, KeyE"
                let:createShortcut
                let:shortcutLabel>
                <DropdownItem
                    on:click={() => wrap('[$]', '[/$]')}
                    on:mount={createShortcut}>
                    {tr.editingLatexEquation()}
                    <span class="ps-1 float-end">{shortcutLabel}</span>
                </DropdownItem>
            </WithShortcut>

            <WithShortcut
                shortcut="Control+KeyT, KeyM"
                let:createShortcut
                let:shortcutLabel>
                <DropdownItem
                    on:click={() => wrap('[$$]', '[/$$]')}
                    on:mount={createShortcut}>
                    {tr.editingLatexMathEnv()}
                    <span class="ps-1 float-end">{shortcutLabel}</span>
                </DropdownItem>
            </WithShortcut>
        </DropdownMenu>
    </WithDropdownMenu>

    <ButtonGroupItem>
        <WithShortcut
            shortcut="Control+Shift+KeyX"
            let:createShortcut
            let:shortcutLabel>
            <IconButton
                tooltip={appendInParentheses(tr.editingHtmlEditor(), shortcutLabel)}
                on:click={onHtmlEdit}
                on:mount={createShortcut}>
                {@html xmlIcon}
            </IconButton>
        </WithShortcut>
    </ButtonGroupItem>
</ButtonGroup>
