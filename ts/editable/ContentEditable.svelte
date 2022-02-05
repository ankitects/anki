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
    import type { InputManagerAction } from "../sveltelib/input-manager";
    import type { MirrorAction } from "../sveltelib/mirror-dom";
    import type { ContentEditableAPI } from "./content-editable";
    import {
        customFocusHandling,
        preventBuiltinContentEditableShortcuts,
    } from "./content-editable";

    export let resolve: (editable: HTMLElement) => void;

    export let mirrors: MirrorAction[];
    export let nodes: Writable<DocumentFragment>;

    const mirrorAction = actionList(mirrors);
    const mirrorOptions = { store: nodes };

    export let managers: InputManagerAction[];

    const managerAction = actionList(managers);

    export let api: Partial<ContentEditableAPI>;

    const { setupFocusHandling, flushCaret } = customFocusHandling();

    Object.assign(api, { flushCaret });
</script>

<anki-editable
    contenteditable="true"
    use:resolve
    use:setupFocusHandling
    use:preventBuiltinContentEditableShortcuts
    use:mirrorAction={mirrorOptions}
    use:managerAction={{}}
    on:focus
    on:blur
    on:click={updateAllState}
    on:keyup={updateAllState}
/>

<style lang="scss">
    anki-editable {
        display: block;
        padding: 6px;
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
