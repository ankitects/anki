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

    import { updateAllState, resetAllState } from "components/WithState.svelte";

    export function updateActiveButtons(event: Event) {
        updateAllState(event);
    }

    export function clearActiveButtons() {
        resetAllState(false);
    }
</script>

<script lang="typescript">
    import { setContext } from "svelte";
    import { disabledKey, nightModeKey } from "components/contextKeys";

    import WithTheming from "components/WithTheming.svelte";
    import StickyBar from "components/StickyBar.svelte";

    import NoteTypeButtons from "./NoteTypeButtons.svelte";
    import FormatInlineButtons from "./FormatInlineButtons.svelte";
    import FormatBlockButtons from "./FormatBlockButtons.svelte";
    import ColorButtons from "./ColorButtons.svelte";
    import TemplateButtons from "./TemplateButtons.svelte";

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
        <NoteTypeButtons />
        <FormatInlineButtons />
    </StickyBar>
</WithTheming>
