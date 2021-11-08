<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import type { Writable } from "svelte/store";
    import { updateAllState } from "../components/WithState.svelte";
    import { saveSelection, restoreSelection } from "../domlib/location";
    import type { SelectionLocation } from "../domlib/location";
    import { on } from "../lib/events";

    export let nodes: Writable<DocumentFragment>;
    export let resolve: (editable: HTMLElement) => void;
    export let mirror: (
        editable: HTMLElement,
        params: { store: Writable<DocumentFragment> },
    ) => void;

    let location: SelectionLocation | null = null;

    /* must execute before DOMMirror */
    function saveLocation(editable: Element) {
        return {
            destroy: on(editable, "blur", () => {
                location = saveSelection(editable);
            }),
        };
    }

    /* must execute after DOMMirror */
    function restoreLocation(editable: Element) {
        return {
            destroy: on(editable, "focus", () => {
                if (location) {
                    restoreSelection(editable, location);
                }
            }),
        };
    }
</script>

<anki-editable
    contenteditable="true"
    use:resolve
    use:saveLocation
    use:mirror={{ store: nodes }}
    use:restoreLocation
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
