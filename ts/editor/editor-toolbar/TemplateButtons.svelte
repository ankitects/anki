<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { bridgeCommand } from "@tslib/bridgecommand";
    import * as tr from "@tslib/ftl";
    import { promiseWithResolver } from "@tslib/promise";
    import { registerPackage } from "@tslib/runtime-require";
    import { getPlatformString } from "@tslib/shortcuts";

    import ButtonGroup from "../../components/ButtonGroup.svelte";
    import ButtonGroupItem, {
        createProps,
        setSlotHostContext,
        updatePropsList,
    } from "../../components/ButtonGroupItem.svelte";
    import DynamicallySlottable from "../../components/DynamicallySlottable.svelte";
    import IconButton from "../../components/IconButton.svelte";
    import Shortcut from "../../components/Shortcut.svelte";
    import { context } from "../NoteEditor.svelte";
    import { setFormat } from "../old-editor-adapter";
    import type { RichTextInputAPI } from "../rich-text-input";
    import { editingInputIsRichText } from "../rich-text-input";
    import { micIcon, paperclipIcon } from "./icons";
    import LatexButton from "./LatexButton.svelte";

    const { focusedInput } = context.get();

    const attachmentCombination = "F3";

    let mediaPromise: Promise<string>;
    let resolve: (media: string) => void;

    function resolveMedia(media: string): void {
        resolve?.(media);
    }

    function attachMediaOnFocus(): void {
        if (disabled) {
            return;
        }

        [mediaPromise, resolve] = promiseWithResolver<string>();
        ($focusedInput as RichTextInputAPI).editable.focusHandler.focus.on(
            async () => setFormat("inserthtml", await mediaPromise),
            { once: true },
        );

        bridgeCommand("attach");
    }

    registerPackage("anki/TemplateButtons", {
        resolveMedia,
    });

    const recordCombination = "F5";

    function attachRecordingOnFocus(): void {
        if (disabled) {
            return;
        }

        [mediaPromise, resolve] = promiseWithResolver<string>();
        ($focusedInput as RichTextInputAPI).editable.focusHandler.focus.on(
            async () => setFormat("inserthtml", await mediaPromise),
            { once: true },
        );

        bridgeCommand("record");
    }

    $: disabled = !$focusedInput || !editingInputIsRichText($focusedInput);

    export let api = {};
</script>

<ButtonGroup>
    <DynamicallySlottable
        slotHost={ButtonGroupItem}
        {createProps}
        {updatePropsList}
        {setSlotHostContext}
        {api}
    >
        <ButtonGroupItem>
            <IconButton
                tooltip="{tr.editingAttachPicturesaudiovideo()} ({getPlatformString(
                    attachmentCombination,
                )})"
                iconSize={70}
                {disabled}
                on:click={attachMediaOnFocus}
            >
                {@html paperclipIcon}
            </IconButton>
            <Shortcut
                keyCombination={attachmentCombination}
                on:action={attachMediaOnFocus}
            />
        </ButtonGroupItem>

        <ButtonGroupItem>
            <IconButton
                tooltip="{tr.editingRecordAudio()} ({getPlatformString(
                    recordCombination,
                )})"
                iconSize={70}
                {disabled}
                on:click={attachRecordingOnFocus}
            >
                {@html micIcon}
            </IconButton>
            <Shortcut
                keyCombination={recordCombination}
                on:action={attachRecordingOnFocus}
            />
        </ButtonGroupItem>

        <ButtonGroupItem>
            <LatexButton />
        </ButtonGroupItem>
    </DynamicallySlottable>
</ButtonGroup>
