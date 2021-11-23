<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script context="module" lang="ts">
    export interface ContentEditableAPI {
        flushLocation(): void;
    }
</script>

<script lang="ts">
    import type { Writable } from "svelte/store";
    import { updateAllState } from "../components/WithState.svelte";
    import type { SelectionLocation } from "../domlib/location";
    import { saveSelection, restoreSelection } from "../domlib/location";
    import { on, preventDefault } from "../lib/events";
    import { caretToEnd } from "../lib/dom";
    import { registerShortcut } from "../lib/shortcuts";
    import iterateActions from "../sveltelib/iterate-actions";

    export let nodes: Writable<DocumentFragment>;
    export let resolve: (editable: HTMLElement) => void;

    export let mirrors: Array<
        (editable: HTMLElement, params: { store: Writable<DocumentFragment> }) => void
    >;
    export let managers: Array<(editable: HTMLElement) => void>;
    export let api: Partial<ContentEditableAPI>;

    const mirrorAction = iterateActions(mirrors);
    const managerAction = iterateActions(managers);

    let latestLocation: SelectionLocation | null = null;

    function onFocus(): void {
        if (!latestLocation) {
            return;
        }

        try {
            restoreSelection(editable, latestLocation);
        } catch {
            caretToEnd(editable);
        }
    }

    const locationEvents: (() => void)[] = [];

    export function flushLocation() {
        let removeEvent: (() => void) | undefined;

        while ((removeEvent = locationEvents.pop())) {
            removeEvent();
        }
    }

    Object.assign(api, {
        flushLocation,
    });

    function onBlur(): void {
        latestLocation = saveSelection(editable);

        const removeOnFocus = on(editable, "focus", onFocus, { once: true });

        locationEvents.push(
            removeOnFocus,
            on(editable, "pointerdown", removeOnFocus, { once: true }),
        );
    }

    /* must execute before DOMMirror */
    function saveLocation(editable: HTMLElement) {
        const removeOnBlur = on(editable, "blur", onBlur);

        return {
            destroy() {
                removeOnBlur();
                flushLocation();
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
    use:mirrorAction={{ store: nodes }}
    use:managerAction={{}}
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
