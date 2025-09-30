<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script context="module" lang="ts">
    import type { Writable } from "svelte/store";

    import { resetAllState, updateAllState } from "$lib/components/WithState.svelte";
    import type { DefaultSlotInterface } from "$lib/sveltelib/dynamic-slotting";

    export function updateActiveButtons(event: Event) {
        updateAllState(event);
    }

    export function clearActiveButtons() {
        resetAllState(false);
    }

    export interface RemoveFormat {
        name: string;
        key: string;
        show: boolean;
        active: boolean;
    }

    export interface EditorToolbarAPI {
        toolbar: DefaultSlotInterface;
        notetypeButtons: DefaultSlotInterface;
        inlineButtons: InlineButtonsAPI;
        blockButtons: DefaultSlotInterface;
        templateButtons: DefaultSlotInterface;
        removeFormats: Writable<RemoveFormat[]>;
    }

    /* Our dynamic components */
    import AddonButtons from "./AddonButtons.svelte";

    export const editorToolbar = {
        AddonButtons,
    };

    import contextProperty from "$lib/sveltelib/context-property";

    const key = Symbol("editorToolbar");
    const [context, setContextProperty] = contextProperty<EditorToolbarAPI>(key);

    export { context };
</script>

<script lang="ts">
    import { createEventDispatcher } from "svelte";
    import { writable } from "svelte/store";

    import ButtonToolbar from "$lib/components/ButtonToolbar.svelte";
    import DynamicallySlottable from "$lib/components/DynamicallySlottable.svelte";
    import Item from "$lib/components/Item.svelte";

    import BlockButtons from "./BlockButtons.svelte";
    import ImageOcclusionButton from "./ImageOcclusionButton.svelte";
    import InlineButtons from "./InlineButtons.svelte";
    import NotetypeButtons from "./NotetypeButtons.svelte";
    import OptionsButtons from "./OptionsButtons.svelte";
    import RichTextClozeButtons from "./RichTextClozeButtons.svelte";
    import TemplateButtons from "./TemplateButtons.svelte";
    import type { InlineButtonsAPI } from "./InlineButtons.svelte";

    export let isLegacy = false;
    export let size: number;
    export let wrap: boolean;

    const toolbar = {} as DefaultSlotInterface;
    const notetypeButtons = {} as DefaultSlotInterface;
    const optionsButtons = {} as DefaultSlotInterface;
    const inlineButtons = {} as InlineButtonsAPI;
    const blockButtons = {} as DefaultSlotInterface;
    const templateButtons = {} as DefaultSlotInterface;
    const removeFormats = writable<RemoveFormat[]>([]);

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

    const dispatch = createEventDispatcher();

    let clientHeight: number;
    $: dispatch("heightChange", { height: clientHeight });
</script>

<div class="editor-toolbar" bind:clientHeight>
    <ButtonToolbar {size} {wrap}>
        <DynamicallySlottable slotHost={Item} api={toolbar}>
            <Item id="notetype">
                <NotetypeButtons api={notetypeButtons}>
                    <slot name="notetypeButtons" />
                </NotetypeButtons>
            </Item>

            <Item id="settings">
                <OptionsButtons api={optionsButtons} {isLegacy} />
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

            <Item id="cloze">
                <RichTextClozeButtons />
            </Item>

            <Item id="image-occlusion-button">
                <ImageOcclusionButton />
            </Item>
        </DynamicallySlottable>
    </ButtonToolbar>
</div>

<style lang="scss">
    .editor-toolbar {
        padding: 0 0 4px;
        border-bottom: 1px solid var(--border);
    }
</style>
