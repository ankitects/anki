<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script context="module" lang="typescript">
    import "./legacy.css";
    import { writable } from "svelte/store";

    const disabled = writable(false);

    export function enableButtons(): void {
        disabled.set(false);
    }

    export function disableButtons(): void {
        disabled.set(true);
    }

    // @ts-expect-error insufficient typing of svelte modules
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
    import { setContext } from "svelte";
    import { disabledKey, nightModeKey } from "components/contextKeys";

    import WithTheming from "components/WithTheming.svelte";
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

    export let nightMode: boolean;

    setContext(nightModeKey, nightMode);
    setContext(disabledKey, disabled);

    export let size: number = 30;
    export let wraps: boolean = true;

    $: style = `--toolbar-size: ${size}px; --toolbar-wrap: ${
        wraps ? "wrap" : "nowrap"
    }`;
</script>

<WithTheming {style}>
    <StickyBar>
        <ButtonToolbar api={toolbar}>
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
</WithTheming>
