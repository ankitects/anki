<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import ButtonGroup from "../../components/ButtonGroup.svelte";
    import IconButton from "../../components/IconButton.svelte";
    import Shortcut from "../../components/Shortcut.svelte";
    import * as tr from "../../lib/ftl";
    import { getPlatformString } from "../../lib/shortcuts";
    import { context as noteEditorContext } from "../NoteEditor.svelte";
    import type { RichTextInputAPI } from "../rich-text-input";
    import { editingInputIsRichText } from "../rich-text-input";
    import { getRemoveFormat } from "../surround";
    import { context as editorToolbarContext } from "./EditorToolbar.svelte";
    import { eraserIcon } from "./icons";
    import { arrowIcon } from "./icons";
    import WithDropdown from "../../components/WithDropdown.svelte";
    import { withButton } from "../../components/helpers";
    import DropdownItem from "../../components/DropdownItem.svelte";
    import DropdownMenu from "../../components/DropdownMenu.svelte";
    import Checkbox from "../../components/CheckBox.svelte";

    const { focusedInput } = noteEditorContext.get();
    let { removeFormats } = editorToolbarContext.get();

    /* $: formats = $removeFormats.map */

    $: input = $focusedInput as RichTextInputAPI;
    $: disabled = !editingInputIsRichText($focusedInput);
    $: removeFormat = disabled ? null : getRemoveFormat(input);
    $: activeFormats = $removeFormats
        .filter((format) => format.active)
        .map((format) => format.format);
    $: showFormats = $removeFormats.filter((format) => format.show);

    function remove(): void {
        removeFormat?.removeFormat(activeFormats);
    }

    const keyCombination = "Control+R";
</script>

<ButtonGroup>
    <IconButton
        tooltip="{tr.editingRemoveFormatting()} ({getPlatformString(keyCombination)})"
        {disabled}
        on:click={remove}
        --border-left-radius="5px"
    >
        {@html eraserIcon}
    </IconButton>
    <Shortcut {keyCombination} on:action={remove} />

    <div class="hide-after">
        <WithDropdown
            autoClose="outside"
            let:createDropdown
            --border-right-radius="5px"
        >
            <IconButton
                tooltip="Choose formats"
                {disabled}
                widthMultiplier={0.5}
                on:mount={withButton(createDropdown)}
            >
                {@html arrowIcon}
            </IconButton>

            <DropdownMenu on:mousedown={(event) => event.preventDefault()}>
                {#each showFormats as format}
                    <DropdownItem
                        on:click={() => {
                            format.active = !format.active;
                            removeFormats = removeFormats;
                        }}
                    >
                        <Checkbox bind:value={format.active} />
                        <span class="d-flex-inline ps-3">{format.name}</span>
                    </DropdownItem>
                {/each}
            </DropdownMenu>
        </WithDropdown>
    </div>
</ButtonGroup>

<style lang="scss">
    .hide-after {
        display: contents;

        :global(.dropdown-toggle::after) {
            display: none;
        }
    }
</style>
