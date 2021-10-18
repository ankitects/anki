<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import type { Writable } from "svelte/store";
    import { updateAllState } from "../components/WithState.svelte";

    export let nodes: Writable<DocumentFragment>;
    export let resolve: (editable: HTMLElement) => void;
    export let mirror: (
        editable: HTMLElement,
        params: { store: Writable<DocumentFragment> },
    ) => void;
</script>

<anki-editable
    contenteditable="true"
    use:resolve
    use:mirror={{ store: nodes }}
    on:focus
    on:blur
    on:click={updateAllState}
    on:keyup={updateAllState}
/>

<style lang="scss">
    anki-editable {
        display: block;
        overflow-wrap: break-word;
        overflow: auto;
        padding: 6px;

        &:focus {
            outline: none;
        }
    }

    /* editable-base.scss contains styling targeting user HTML */
</style>
