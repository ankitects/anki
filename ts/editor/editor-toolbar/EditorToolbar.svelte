<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script context="module" lang="ts">
    import { updateAllState, resetAllState } from "../../components/WithState.svelte";

    import type { ButtonGroupAPI } from "../../components/ButtonGroup.svelte";
    import type { ButtonToolbarAPI } from "../../components/ButtonToolbar.svelte";

    export function updateActiveButtons(event: Event) {
        updateAllState(event);
    }

    export function clearActiveButtons() {
        resetAllState(false);
    }

    export interface EditorToolbarAPI {
        toolbar: ButtonToolbarAPI;
        notetypeButtons: ButtonGroupAPI;
        formatInlineButtons: ButtonGroupAPI;
        formatBlockButtons: ButtonGroupAPI;
        colorButtons: ButtonGroupAPI;
        templateButtons: ButtonGroupAPI;
    }
</script>

<script lang="ts">
    import StickyContainer from "../../components/StickyContainer.svelte";
    import ButtonToolbar from "../../components/ButtonToolbar.svelte";
    import Item from "../../components/Item.svelte";

    import NoteTypeButtons from "./NoteTypeButtons.svelte";
    import FormatInlineButtons from "./FormatInlineButtons.svelte";
    import FormatBlockButtons from "./FormatBlockButtons.svelte";
    import ColorButtons from "./ColorButtons.svelte";
    import TemplateButtons from "./TemplateButtons.svelte";

    export let size: number;
    export let wrap: boolean;

    export let textColor: string;
    export let highlightColor: string;

    const toolbar = {};
    const notetypeButtons = {};
    const formatInlineButtons = {};
    const formatBlockButtons = {};
    const colorButtons = {};
    const templateButtons = {};

    export let api: Partial<EditorToolbarAPI> = {};

    Object.assign(api, {
        toolbar,
        notetypeButtons,
        formatInlineButtons,
        formatBlockButtons,
        colorButtons,
        templateButtons,
    } as EditorToolbarAPI);
</script>

<StickyContainer --gutter-block="0.1rem" --sticky-borders="0 0 1px">
    <ButtonToolbar {size} {wrap} api={toolbar}>
        <Item id="notetype">
            <NoteTypeButtons api={notetypeButtons} />
        </Item>

        <Item id="inlineFormatting">
            <FormatInlineButtons api={formatInlineButtons} />
        </Item>

        <Item id="blockFormatting">
            <FormatBlockButtons api={formatBlockButtons} />
        </Item>

        <Item id="color">
            <ColorButtons {textColor} {highlightColor} api={colorButtons} />
        </Item>

        <Item id="template">
            <TemplateButtons api={templateButtons} />
        </Item>
    </ButtonToolbar>
</StickyContainer>
