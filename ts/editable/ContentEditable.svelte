<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script context="module" lang="ts">
    export type { ContentEditableAPI } from "./content-editable";
</script>

<script lang="ts">
    import type { Writable } from "svelte/store";

    import { updateAllState } from "../components/WithState.svelte";
    import actionList from "../sveltelib/action-list";
    import type { MirrorAction } from "../sveltelib/dom-mirror";
    import type { SetupInputHandlerAction } from "../sveltelib/input-handler";
    import type { ContentEditableAPI } from "./content-editable";
    import {
        fixRTLKeyboardNav,
        preventBuiltinShortcuts,
        useFocusHandler,
    } from "./content-editable";

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
    contenteditable="true"
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
/>

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
    }

    /* editable-base.scss contains styling targeting user HTML */
</style>
