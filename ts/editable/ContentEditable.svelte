<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import type { Writable } from "svelte/store";
    import { updateAllState } from "../components/WithState.svelte";
    import { saveSelection, restoreSelection } from "../domlib/location";
    import { on, preventDefault } from "../lib/events";
    import { registerShortcut } from "../lib/shortcuts";

    export let nodes: Writable<DocumentFragment>;
    export let resolve: (editable: HTMLElement) => void;
    export let mirror: (
        editable: HTMLElement,
        params: { store: Writable<DocumentFragment> },
    ) => void;

    export let inputManager: (editable: HTMLElement) => void;

    let removeOnFocus: () => void;
    let removeOnPointerdown: () => void;

    function onBlur(): void {
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
    }

    /* must execute before DOMMirror */
    function saveLocation(editable: HTMLElement) {
        const removeOnBlur = on(editable, "blur", onBlur);

        return {
            destroy() {
                removeOnBlur();
                removeOnFocus?.();
                removeOnPointerdown?.();
            },
        };
    }

    let editable: HTMLElement;

    $: if (editable) {
        for (const keyCombination of [
            "Control+B",
            "Control+U",
            "Control+I",
            "Control+R",
        ]) {
            registerShortcut(preventDefault, keyCombination, editable);
        }
    }
</script>

<anki-editable
    contenteditable="true"
    bind:this={editable}
    use:resolve
    use:saveLocation
    use:mirror={{ store: nodes }}
    use:inputManager
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
