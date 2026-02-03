<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script context="module" lang="ts">
    export type { ContentEditableAPI } from "./content-editable";
</script>

<script lang="ts">
    import type { Writable } from "svelte/store";

    import { updateAllState } from "$lib/components/WithState.svelte";
    import actionList from "$lib/sveltelib/action-list";
    import type { MirrorAction } from "$lib/sveltelib/dom-mirror";
    import type { SetupInputHandlerAction } from "$lib/sveltelib/input-handler";

    import type { ContentEditableAPI } from "./content-editable";
    import {
        fixRTLKeyboardNav,
        preventBuiltinShortcuts,
        useFocusHandler,
    } from "./content-editable";
    import { pageTheme } from "$lib/sveltelib/theme";

    export let resolve: (editable: HTMLElement) => void;

    export let mirrors: MirrorAction[];
    export let nodes: Writable<DocumentFragment>;

    const mirrorAction = actionList(mirrors);
    const mirrorOptions = { store: nodes };

    export let inputHandlers: SetupInputHandlerAction[];

    const inputHandlerAction = actionList(inputHandlers);

    export let api: Partial<ContentEditableAPI>;

    const [focusHandler, setupFocusHandling] = useFocusHandler();

    Object.assign(api, { focusHandler });
</script>

<anki-editable
    class:nightMode={$pageTheme.isDark}
    contenteditable="true"
    role="textbox"
    tabindex="0"
    use:resolve
    use:setupFocusHandling
    use:preventBuiltinShortcuts
    use:fixRTLKeyboardNav
    use:mirrorAction={mirrorOptions}
    use:inputHandlerAction={{}}
    on:focus
    on:blur
    on:click={updateAllState}
    on:keyup={updateAllState}
></anki-editable>

<style lang="scss">
    anki-editable {
        display: block;
        position: relative;

        overflow: auto;
        overflow-wrap: anywhere;
        /* fallback for iOS */
        word-break: break-word;

        &:focus {
            outline: none;
        }

        min-height: 1.5em;
    }

    /* editable-base.scss contains styling targeting user HTML */
</style>
