<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script context="module" lang="ts">
    import type { Writable } from "svelte/store";

    import { resetAllState, updateAllState } from "../../components/WithState.svelte";
    import type { SurroundFormat } from "../../domlib/surround";
    import type { DefaultSlotInterface } from "../../sveltelib/dynamic-slotting";

    export function updateActiveButtons(event: Event) {
        updateAllState(event);
    }

    export function clearActiveButtons() {
        resetAllState(false);
    }

    export interface RemoveFormat<T> {
        name: string;
        show: boolean;
        active: boolean;
        format: SurroundFormat<T>;
    }

    export interface EditorToolbarAPI {
        toolbar: DefaultSlotInterface;
        notetypeButtons: DefaultSlotInterface;
        inlineButtons: DefaultSlotInterface;
        blockButtons: DefaultSlotInterface;
        templateButtons: DefaultSlotInterface;
        removeFormats: Writable<RemoveFormat<any>[]>;
    }

    /* Our dynamic components */
    import AddonButtons from "./AddonButtons.svelte";

    export const editorToolbar = {
        AddonButtons,
    };

    import contextProperty from "../../sveltelib/context-property";

    const key = Symbol("editorToolbar");
    const [context, setContextProperty] = contextProperty<EditorToolbarAPI>(key);

    export { context };
</script>

<script lang="ts">
    import { writable } from "svelte/store";

    import ButtonToolbar from "../../components/ButtonToolbar.svelte";
    import DynamicallySlottable from "../../components/DynamicallySlottable.svelte";
    import Item from "../../components/Item.svelte";
    import StickyContainer from "../../components/StickyContainer.svelte";
    import BlockButtons from "./BlockButtons.svelte";
    import InlineButtons from "./InlineButtons.svelte";
    import NotetypeButtons from "./NotetypeButtons.svelte";
    import TemplateButtons from "./TemplateButtons.svelte";

    export let size: number;
    export let wrap: boolean;

    const toolbar = {} as DefaultSlotInterface;
    const notetypeButtons = {} as DefaultSlotInterface;
    const inlineButtons = {} as DefaultSlotInterface;
    const blockButtons = {} as DefaultSlotInterface;
    const templateButtons = {} as DefaultSlotInterface;
    const removeFormats = writable<RemoveFormat<any>[]>([]);

    let apiPartial: Partial<EditorToolbarAPI> = {};
    export { apiPartial as api };

    const api: EditorToolbarAPI = Object.assign(apiPartial, {
        toolbar,
        notetypeButtons,
        inlineButtons,
        blockButtons,
        templateButtons,
        removeFormats,
    } as EditorToolbarAPI);

    setContextProperty(api);
</script>

<StickyContainer --gutter-block="0.1rem" --sticky-borders="0 0 1px">
    <ButtonToolbar {size} {wrap}>
        <DynamicallySlottable slotHost={Item} api={toolbar}>
            <Item id="notetype">
                <NotetypeButtons api={notetypeButtons}>
                    <slot name="notetypeButtons" />
                </NotetypeButtons>
            </Item>

            <Item id="inlineFormatting">
                <InlineButtons api={inlineButtons} />
            </Item>

            <Item id="blockFormatting">
                <BlockButtons api={blockButtons} />
            </Item>

            <Item id="template">
                <TemplateButtons api={templateButtons} />
            </Item>
        </DynamicallySlottable>
    </ButtonToolbar>
</StickyContainer>
