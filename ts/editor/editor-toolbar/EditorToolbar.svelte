<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script context="module" lang="ts">
    import { resetAllState, updateAllState } from "../../components/WithState.svelte";
    import type { SurroundFormat } from "../../domlib/surround";
    import type { DefaultSlotInterface } from "../../sveltelib/dynamic-slotting";

    export function updateActiveButtons(event: Event) {
        updateAllState(event);
    }

    export function clearActiveButtons() {
        resetAllState(false);
    }

    export interface EditorToolbarAPI {
        toolbar: DefaultSlotInterface;
        notetypeButtons: DefaultSlotInterface;
        formatInlineButtons: DefaultSlotInterface;
        formatBlockButtons: DefaultSlotInterface;
        colorButtons: DefaultSlotInterface;
        templateButtons: DefaultSlotInterface;
        removeFormats: SurroundFormat[];
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
    import ButtonGroup from "../../components/ButtonGroup.svelte";
    import ButtonToolbar from "../../components/ButtonToolbar.svelte";
    import DynamicallySlottable from "../../components/DynamicallySlottable.svelte";
    import Item from "../../components/Item.svelte";
    import StickyContainer from "../../components/StickyContainer.svelte";
    import FormatBlockButtons from "./FormatBlockButtons.svelte";
    import FormatInlineButtons from "./FormatInlineButtons.svelte";
    import NotetypeButtons from "./NotetypeButtons.svelte";
    import RemoveFormatButton from "./RemoveFormatButton.svelte";
    import TemplateButtons from "./TemplateButtons.svelte";

    export let size: number;
    export let wrap: boolean;

    const toolbar = {};
    const notetypeButtons = {};
    const formatInlineButtons = {};
    const formatBlockButtons = {};
    const colorButtons = {};
    const templateButtons = {};
    const removeFormats: SurroundFormat[] = [];

    let apiPartial: Partial<EditorToolbarAPI> = {};
    export { apiPartial as api };

    const api: EditorToolbarAPI = Object.assign(apiPartial, {
        toolbar,
        notetypeButtons,
        formatInlineButtons,
        formatBlockButtons,
        colorButtons,
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
                <FormatInlineButtons api={formatInlineButtons} />
            </Item>

            <ButtonGroup>
                <RemoveFormatButton
                    --border-left-radius="5px"
                    --border-right-radius="5px"
                />
            </ButtonGroup>

            <Item id="blockFormatting">
                <FormatBlockButtons api={formatBlockButtons} />
            </Item>

            <Item id="template">
                <TemplateButtons api={templateButtons} />
            </Item>
        </DynamicallySlottable>
    </ButtonToolbar>
</StickyContainer>
