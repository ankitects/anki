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

    /* Export components */
    import AddonButtons from "./AddonButtons.svelte";
    import PreviewButton from "./PreviewButton.svelte";
    import LabelButton from "components/LabelButton.svelte";
    import IconButton from "components/IconButton.svelte";

    export const editorToolbar = {
        AddonButtons,
        PreviewButton,
        LabelButton,
        IconButton,
    };
</script>

<script lang="typescript">
    import StickyBar from "components/StickyBar.svelte";
    import ButtonToolbar from "components/ButtonToolbar.svelte";
    import ButtonToolbarItem from "components/ButtonToolbarItem.svelte";

    import NoteTypeButtons from "./NoteTypeButtons.svelte";
    import FormatInlineButtons from "./FormatInlineButtons.svelte";
    import FormatBlockButtons from "./FormatBlockButtons.svelte";
    import ColorButtons from "./ColorButtons.svelte";
    import TemplateButtons from "./TemplateButtons.svelte";

    export const toolbar = {};
    export const notetypeButtons = {};
    export const formatInlineButtons = {};
    export const formatBlockButtons = {};
    export const colorButtons = {};
    export const templateButtons = {};

    export let size = 2;
    export let wrap = true;
</script>

<StickyBar>
    <ButtonToolbar {size} {wrap} api={toolbar}>
        <ButtonToolbarItem id="notetype">
            <NoteTypeButtons api={notetypeButtons} />
        </ButtonToolbarItem>

        <ButtonToolbarItem id="inlineFormatting">
            <FormatInlineButtons api={formatInlineButtons} />
        </ButtonToolbarItem>

        <ButtonToolbarItem id="blockFormatting">
            <FormatBlockButtons api={formatBlockButtons} />
        </ButtonToolbarItem>

        <ButtonToolbarItem id="color">
            <ColorButtons api={colorButtons} />
        </ButtonToolbarItem>

        <ButtonToolbarItem id="template">
            <TemplateButtons api={templateButtons} />
        </ButtonToolbarItem>
    </ButtonToolbar>
</StickyBar>
