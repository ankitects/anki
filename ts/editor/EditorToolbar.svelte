<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script context="module" lang="typescript">
    import "./legacy.css";
    // @ts-expect-error Insufficient typing
    import { updateAllState, resetAllState } from "components/WithState.svelte";

    export function updateActiveButtons(event: Event) {
        updateAllState(event);
    }

    export function clearActiveButtons() {
        resetAllState(false);
    }

    /* Our dynamic components */
    import AddonButtons from "./AddonButtons.svelte";
    import PreviewButton from "./PreviewButton.svelte";

    export const editorToolbar = {
        AddonButtons,
        PreviewButton,
    };
</script>

<script lang="typescript">
    import { isApplePlatform } from "lib/platform";
    import StickyBar from "components/StickyBar.svelte";
    import ButtonToolbar from "components/ButtonToolbar.svelte";
    import Item from "components/Item.svelte";

    import NoteTypeButtons from "./NoteTypeButtons.svelte";
    import FormatInlineButtons from "./FormatInlineButtons.svelte";
    import FormatBlockButtons from "./FormatBlockButtons.svelte";
    import ColorButtons from "./ColorButtons.svelte";
    import TemplateButtons from "./TemplateButtons.svelte";

    export let size = isApplePlatform() ? 1.6 : 2.0;
    export let wrap = true;

    export let textColor: string;
    export let highlightColor: string;

    export const toolbar = {};
    export const notetypeButtons = {};
    export const formatInlineButtons = {};
    export const formatBlockButtons = {};
    export const colorButtons = {};
    export const templateButtons = {};
</script>

<StickyBar>
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
</StickyBar>
