<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "@generated/ftl";
    import { promiseWithResolver } from "@tslib/promise";
    import { registerPackage } from "@tslib/runtime-require";
    import { getPlatformString } from "@tslib/shortcuts";

    import ButtonGroup from "$lib/components/ButtonGroup.svelte";
    import ButtonGroupItem, {
        createProps,
        setSlotHostContext,
        updatePropsList,
    } from "$lib/components/ButtonGroupItem.svelte";
    import DynamicallySlottable from "$lib/components/DynamicallySlottable.svelte";
    import Icon from "$lib/components/Icon.svelte";
    import IconButton from "$lib/components/IconButton.svelte";
    import { micIcon, paperclipIcon } from "$lib/components/icons";
    import Shortcut from "$lib/components/Shortcut.svelte";

    import { context } from "../NoteEditor.svelte";
    import { setFormat } from "../old-editor-adapter";
    import type { RichTextInputAPI } from "../rich-text-input";
    import { editingInputIsRichText } from "../rich-text-input";
    import LatexButton from "./LatexButton.svelte";
    import { filenameToLink, openFilePickerForMedia } from "../rich-text-input/data-transfer";
    import { addMediaFromPath, recordAudio } from "@generated/backend";

    const { focusedInput } = context.get();

    const attachmentCombination = "F3";

    let mediaPromise: Promise<string>;
    let resolve: (media: string) => void;

    function resolveMedia(media: string): void {
        resolve?.(media);
    }

    async function attachPath(path: string): Promise<void> {
        const filename = (await addMediaFromPath({ path })).val;
        resolveMedia(filenameToLink(filename));
    }

    async function attachMediaOnFocus(): Promise<void> {
        if (disabled) {
            return;
        }

        [mediaPromise, resolve] = promiseWithResolver<string>();
        ($focusedInput as RichTextInputAPI).editable.focusHandler.focus.on(
            async () => setFormat("inserthtml", await mediaPromise),
            { once: true },
        );
        const path = await openFilePickerForMedia();
        await attachPath(path);
    }

    registerPackage("anki/TemplateButtons", {
        resolveMedia,
    });

    const recordCombination = "F5";

    async function attachRecordingOnFocus(): Promise<void> {
        if (disabled) {
            return;
        }

        [mediaPromise, resolve] = promiseWithResolver<string>();
        ($focusedInput as RichTextInputAPI).editable.focusHandler.focus.on(
            async () => setFormat("inserthtml", await mediaPromise),
            { once: true },
        );
        const path = (await recordAudio({})).val;
        await attachPath(path);
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
                <Icon icon={paperclipIcon} />
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
                <Icon icon={micIcon} />
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
