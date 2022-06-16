<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script context="module" lang="ts">
    export type { ContentEditableAPI } from "./content-editable";
</script>

<script lang="ts">
    import { getContext } from "svelte";
    import type { Readable, Writable } from "svelte/store";

    import { updateAllState } from "../components/WithState.svelte";
    import { descriptionKey } from "../lib/context-keys";
    import actionList from "../sveltelib/action-list";
    import type { MirrorAction } from "../sveltelib/dom-mirror";
    import type { SetupInputHandlerAction } from "../sveltelib/input-handler";
    import type { ContentEditableAPI } from "./content-editable";
    import { preventBuiltinShortcuts, useFocusHandler } from "./content-editable";

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

    const description = getContext<Readable<string>>(descriptionKey);
    $: descriptionCSSValue = `"${$description}"`;

    let innerHTML = "";
    $: empty = ["", "<br>"].includes(innerHTML);
</script>

<anki-editable
    class:empty
    contenteditable="true"
    bind:innerHTML
    use:resolve
    use:setupFocusHandling
    use:preventBuiltinShortcuts
    use:mirrorAction={mirrorOptions}
    use:inputHandlerAction={{}}
    on:focus
    on:blur
    on:click={updateAllState}
    on:keyup={updateAllState}
    style="--description: {descriptionCSSValue}"
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
        &.empty::after {
            content: var(--description);
            opacity: 0.4;
            cursor: text;
        }
    }

    /* editable-base.scss contains styling targeting user HTML */
</style>
