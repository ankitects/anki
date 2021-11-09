<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import type { Writable } from "svelte/store";
    import { updateAllState } from "../components/WithState.svelte";
    import { saveSelection, restoreSelection } from "../domlib/location";
    import { on } from "../lib/events";

    export let nodes: Writable<DocumentFragment>;
    export let resolve: (editable: HTMLElement) => void;
    export let mirror: (
        editable: HTMLElement,
        params: { store: Writable<DocumentFragment> },
    ) => void;

    /* must execute before DOMMirror */
    function saveLocation(editable: Element) {
        let removeOnFocus: () => void;
        let removeOnPointerdown: () => void;

        const removeOnBlur = on(editable, "blur", () => {
            const location = saveSelection(editable);

            removeOnFocus = on(
                editable,
                "focus",
                () => {
                    if (location) {
                        restoreSelection(editable, location);
                    }
                },
                { once: true },
            );

            removeOnPointerdown = on(editable, "pointerdown", () => removeOnFocus?.(), {
                once: true,
            });
        });

        return {
            destroy() {
                removeOnBlur();
                removeOnFocus?.();
                removeOnPointerdown?.();
            },
        };
    }
</script>

<anki-editable
    contenteditable="true"
    use:resolve
    use:saveLocation
    use:mirror={{ store: nodes }}
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
