<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script context="module" lang="ts">
    import type { Writable } from "svelte/store";

    import Collapsible from "$lib/components/Collapsible.svelte";

    import type { EditingInputAPI } from "./EditingArea.svelte";
    import type { EditorToolbarAPI } from "./editor-toolbar";
    import type { EditorFieldAPI } from "./EditorField.svelte";
    import FieldState from "./FieldState.svelte";
    import LabelContainer from "./LabelContainer.svelte";
    import LabelName from "./LabelName.svelte";
    import type { EditorMode } from "./types";

    export interface NoteEditorAPI {
        fields: EditorFieldAPI[];
        hoveredField: Writable<EditorFieldAPI | null>;
        focusedField: Writable<EditorFieldAPI | null>;
        focusedInput: Writable<EditingInputAPI | null>;
        toolbar: EditorToolbarAPI;
    }

    import { registerPackage } from "@tslib/runtime-require";

    import contextProperty from "$lib/sveltelib/context-property";
    import lifecycleHooks from "$lib/sveltelib/lifecycle-hooks";

    const key = Symbol("noteEditor");
    const [context, setContextProperty] = contextProperty<NoteEditorAPI>(key);
    const [lifecycle, instances, setupLifecycleHooks] = lifecycleHooks<NoteEditorAPI>();

    export { context };

    registerPackage("anki/NoteEditor", {
        context,
        lifecycle,
        instances,
    });
</script>

<script lang="ts">
    import * as tr from "@generated/ftl";
    import { bridgeCommand } from "@tslib/bridgecommand";
    import { onMount, tick } from "svelte";
    import { get, writable } from "svelte/store";
    import { nodeIsCommonElement } from "@tslib/dom";

    import Absolute from "$lib/components/Absolute.svelte";
    import Badge from "$lib/components/Badge.svelte";
    import Icon from "$lib/components/Icon.svelte";
    import { alertIcon } from "$lib/components/icons";
    import { TagEditor } from "$lib/tag-editor";
    import { commitTagEdits } from "$lib/tag-editor/TagInput.svelte";

    import {
        type ImageLoadedEvent,
        resetIOImage,
    } from "../image-occlusion/mask-editor";
    import { ChangeTimer } from "$lib/editable/change-timer";
    import { clearableArray } from "./destroyable";
    import DuplicateLink from "./DuplicateLink.svelte";
    import EditorToolbar from "./editor-toolbar";
    import type { FieldData } from "./EditorField.svelte";
    import EditorField from "./EditorField.svelte";
    import Fields from "./Fields.svelte";
    import ImageOverlay from "./image-overlay";
    import { shrinkImagesByDefault } from "./image-overlay/ImageOverlay.svelte";
    import MathjaxOverlay from "./mathjax-overlay";
    import { closeMathjaxEditor } from "./mathjax-overlay/MathjaxEditor.svelte";
    import Notification from "./Notification.svelte";
    import PlainTextInput from "./plain-text-input";
    import { closeHTMLTags } from "./plain-text-input/PlainTextInput.svelte";
    import PlainTextBadge from "./PlainTextBadge.svelte";
    import RichTextInput, { editingInputIsRichText } from "./rich-text-input";
    import RichTextBadge from "./RichTextBadge.svelte";
    import type { NotetypeIdAndModTime, SessionOptions } from "./types";
    import { EditorState } from "./types";

    function quoteFontFamily(fontFamily: string): string {
        // generic families (e.g. sans-serif) must not be quoted
        if (!/^[-a-z]+$/.test(fontFamily)) {
            fontFamily = `"${fontFamily}"`;
        }
        return fontFamily;
    }

    const size = 1.6;
    const wrap = true;

    const sessionOptions: SessionOptions = {};
    export function saveSession(): void {
        if (notetypeMeta) {
            sessionOptions[notetypeMeta.id.toString()] = {
                fieldsCollapsed,
                fieldStates: {
                    richTextsHidden,
                    plainTextsHidden,
                    plainTextDefaults,
                },
                modTimeOfNotetype: notetypeMeta.modTime,
            };
        }
    }

    const fieldStores: Writable<string>[] = [];
    let fieldNames: string[] = [];
    export function setFields(newFieldNames: string[], fieldValues: string[]): void {
        fieldNames = newFieldNames;

        for (let i = fieldStores.length; i < fieldValues.length; i++) {
            const newStore = writable("");
            fieldStores[i] = newStore;
            newStore.subscribe((value) => updateField(i, value));
        }

        for (
            let i = fieldStores.length;
            i > fieldValues.length;
            i = fieldStores.length
        ) {
            fieldStores.pop();
        }

        for (const [index, value] of fieldValues.entries()) {
            fieldStores[index].set(value);
        }
    }

    let fieldsCollapsed: boolean[] = [];
    export function setCollapsed(defaultCollapsed: boolean[]): void {
        fieldsCollapsed =
            sessionOptions[notetypeMeta?.id?.toString()]?.fieldsCollapsed ??
            defaultCollapsed;
    }
    let clozeFields: boolean[] = [];
    export function setClozeFields(defaultClozeFields: boolean[]): void {
        clozeFields = defaultClozeFields;
    }

    let richTextsHidden: boolean[] = [];
    let plainTextsHidden: boolean[] = [];
    let plainTextDefaults: boolean[] = [];

    export function setPlainTexts(defaultPlainTexts: boolean[]): void {
        const states = sessionOptions[notetypeMeta?.id?.toString()]?.fieldStates;
        if (states) {
            richTextsHidden = states.richTextsHidden;
            plainTextsHidden = states.plainTextsHidden;
            plainTextDefaults = states.plainTextDefaults;
        } else {
            plainTextDefaults = defaultPlainTexts;
            richTextsHidden = [...defaultPlainTexts];
            plainTextsHidden = Array.from(defaultPlainTexts, (v) => !v);
        }
    }

    export function triggerChanges(): void {
        // I know this looks quite weird and doesn't seem to do anything
        // but if we don't call this after setPlainTexts() and setCollapsed()
        // when switching notetypes, existing collapsibles won't react
        // automatically to the updated props
        tick().then(() => {
            fieldsCollapsed = fieldsCollapsed;
            plainTextDefaults = plainTextDefaults;
            richTextsHidden = richTextsHidden;
            plainTextsHidden = plainTextsHidden;
        });
    }

    function setMathjaxEnabled(enabled: boolean): void {
        mathjaxConfig.enabled = enabled;
    }

    let fieldDescriptions: string[] = [];
    export function setDescriptions(descriptions: string[]): void {
        fieldDescriptions = descriptions.map((d) =>
            d.replace(/\\/g, "").replace(/"/g, '\\"'),
        );
    }

    let fonts: [string, number, boolean][] = [];

    const fields = clearableArray<EditorFieldAPI>();

    export function setFonts(fs: [string, number, boolean][]): void {
        fonts = fs;
    }

    let stickies: boolean[] = [];

    function setSticky(stckies: boolean[]): void {
        stickies = stckies;
    }

    export function focusField(index: number | null): void {
        tick().then(() => {
            if (typeof index === "number") {
                if (!(index in fields)) {
                    return;
                }

                fields[index].editingArea?.refocus();
            } else {
                $focusedInput?.refocus();
            }
        });
    }

    const tags = writable<string[]>([]);
    export function setTags(ts: string[]): void {
        $tags = ts;
    }

    const tagsCollapsed = writable<boolean>();
    export function setTagsCollapsed(collapsed: boolean): void {
        $tagsCollapsed = collapsed;
    }

    function updateTagsCollapsed(collapsed: boolean) {
        $tagsCollapsed = collapsed;
        bridgeCommand(`setTagsCollapsed:${$tagsCollapsed}`);
    }

    let note: Note | null = null;
    export function setNote(n: Note): void {
        note = n;
        // TODO this is a hack, because it requires the NoteEditor to know implementation details of the PlainTextInput.
        // It should be refactored once we work on our own Undo stack
        for (const pi of plainTextInputs) {
            pi.api.codeMirror.editor.then((editor) => editor.clearHistory());
        }
    }

    let notetypeMeta: NotetypeIdAndModTime;
    function setNotetypeMeta(notetype: Notetype): void {
        notetypeMeta = { id: notetype.id, modTime: notetype.mtimeSecs };
        // Discard the saved state of the fields if the notetype has been modified.
        if (
            sessionOptions[notetype.id.toString()]?.modTimeOfNotetype !==
            notetype.mtimeSecs
        ) {
            delete sessionOptions[notetype.id.toString()];
        }
        if (isImageOcclusion) {
            getImageOcclusionFields({
                notetypeId: BigInt(notetype.id),
            }).then((r) => (ioFields = r.fields!));
        }
    }

    let isImageOcclusion = false;
    function setIsImageOcclusion(val: boolean) {
        isImageOcclusion = val;
        $ioMaskEditorVisible = val;
    }

    let cols: ("dupe" | "")[] = [];
    export function setBackgrounds(cls: ("dupe" | "")[]): void {
        cols = cls;
    }

    let hint: string = "";
    export function setClozeHint(hnt: string): void {
        hint = hnt;
    }

    $: fieldsData = fieldNames.map((name, index) => ({
        name,
        plainText: plainTextDefaults[index],
        description: fieldDescriptions[index],
        fontFamily: quoteFontFamily(fonts[index][0]),
        fontSize: fonts[index][1],
        direction: fonts[index][2] ? "rtl" : "ltr",
        collapsed: fieldsCollapsed[index],
        hidden: hideFieldInOcclusionType(index, ioFields),
        isClozeField: clozeFields[index],
    })) as FieldData[];

    let lastSavedTags: string[] | null = null;
    function saveTags({ detail }: CustomEvent): void {
        tagAmount = detail.tags.filter((tag: string) => tag != "").length;
        lastSavedTags = detail.tags;
        bridgeCommand(`saveTags:${JSON.stringify(detail.tags)}`);
    }

    const fieldSave = new ChangeTimer();

    function transformContentBeforeSave(content: string): string {
        return content.replace(/ data-editor-shrink="(true|false)"/g, "");
        // TODO: mungeHTML()
    }

    async function updateCurrentNote() {
        if (mode !== "add") {
            await editorUpdateNote({
                notes: [note!],
                skipUndoEntry: false,
            });
        }
    }

    function updateField(index: number, content: string): void {
        fieldSave.schedule(() => {
            bridgeCommand(`key:${index}`);
            note!.fields[index] = transformContentBeforeSave(content);
            updateCurrentNote();
        }, 600);
    }

    function saveFieldNow(): void {
        /* this will always be a key save */
        fieldSave.fireImmediately();
    }

    function saveNow(): void {
        closeMathjaxEditor?.();
        $commitTagEdits();
        saveFieldNow();
    }

    export function saveOnPageHide() {
        if (document.visibilityState === "hidden") {
            // will fire on session close and minimize
            saveFieldNow();
        }
    }

    export function focusIfField(x: number, y: number): boolean {
        const elements = document.elementsFromPoint(x, y);
        const first = elements[0].closest(".field-container");

        if (!first || !nodeIsCommonElement(first)) {
            return false;
        }

        const index = parseInt(first.dataset?.index ?? "");

        if (Number.isNaN(index) || !fields[index] || fieldsCollapsed[index]) {
            return false;
        }

        if (richTextsHidden[index]) {
            toggleRichTextInput(index);
        } else {
            richTextInputs[index].api.refocus();
        }

        return true;
    }

    let richTextInputs: RichTextInput[] = [];
    $: richTextInputs = richTextInputs.filter(Boolean);

    let plainTextInputs: PlainTextInput[] = [];
    $: plainTextInputs = plainTextInputs.filter(Boolean);

    function toggleRichTextInput(index: number): void {
        const hidden = !richTextsHidden[index];
        richTextInputs[index].focusFlag.setFlag(!hidden);
        richTextsHidden[index] = hidden;
        if (hidden) {
            plainTextInputs[index].api.refocus();
        }
    }

    function togglePlainTextInput(index: number): void {
        const hidden = !plainTextsHidden[index];
        plainTextInputs[index].focusFlag.setFlag(!hidden);
        plainTextsHidden[index] = hidden;
        if (hidden) {
            richTextInputs[index].api.refocus();
        }
    }

    function toggleField(index: number): void {
        const collapsed = !fieldsCollapsed[index];
        fieldsCollapsed[index] = collapsed;

        const defaultInput = !plainTextDefaults[index]
            ? richTextInputs[index]
            : plainTextInputs[index];

        if (!collapsed) {
            defaultInput.api.refocus();
        } else if (!plainTextDefaults[index]) {
            plainTextsHidden[index] = true;
        } else {
            richTextsHidden[index] = true;
        }
    }

    const toolbar: Partial<EditorToolbarAPI> = {};

    function setShrinkImages(shrinkByDefault: boolean) {
        $shrinkImagesByDefault = shrinkByDefault;
    }

    function setCloseHTMLTags(closeTags: boolean) {
        $closeHTMLTags = closeTags;
    }

    /**
     * Enable/Disable add-on buttons that do not have the `perm` class
     */
    function setAddonButtonsDisabled(disabled: boolean): void {
        document
            .querySelectorAll<HTMLButtonElement>("button.linkb:not(.perm)")
            .forEach((button) => {
                button.disabled = disabled;
            });
    }

    import { ImageOcclusionFieldIndexes } from "@generated/anki/image_occlusion_pb";

    import { StockNotetype_OriginalStockKind } from "@generated/anki/notetypes_pb";
    import type { Notetype } from "@generated/anki/notetypes_pb";
    import {
        getFieldNames,
        getClozeFieldOrds,
        getImageOcclusionFields,
        getNote,
        getNotetype,
        encodeIriPaths,
        newNote,
        editorUpdateNote,
    } from "@generated/backend";
    import { wrapInternal } from "@tslib/wrap";

    import Shortcut from "$lib/components/Shortcut.svelte";

    import { mathjaxConfig } from "$lib/editable/mathjax-element.svelte";
    import ImageOcclusionPage from "../image-occlusion/ImageOcclusionPage.svelte";
    import ImageOcclusionPicker from "../image-occlusion/ImageOcclusionPicker.svelte";
    import type { IOMode } from "../image-occlusion/lib";
    import { exportShapesToClozeDeletions } from "../image-occlusion/shapes/to-cloze";
    import {
        hideAllGuessOne,
        ioImageLoadedStore,
        ioMaskEditorVisible,
    } from "../image-occlusion/store";
    import CollapseLabel from "./CollapseLabel.svelte";
    import * as oldEditorAdapter from "./old-editor-adapter";
    import StickyBadge from "./StickyBadge.svelte";
    import ButtonGroupItem from "$lib/components/ButtonGroupItem.svelte";
    import PreviewButton from "./PreviewButton.svelte";
    import type { Note } from "@generated/anki/notes_pb";

    $: isIOImageLoaded = false;
    $: ioImageLoadedStore.set(isIOImageLoaded);
    let imageOcclusionMode: IOMode | undefined;
    let ioFields = new ImageOcclusionFieldIndexes({});

    function pickIOImage() {
        imageOcclusionMode = undefined;
        bridgeCommand("addImageForOcclusion");
    }

    function pickIOImageFromClipboard() {
        imageOcclusionMode = undefined;
        bridgeCommand("addImageForOcclusionFromClipboard");
    }

    async function setupMaskEditor(options: { html: string; mode: IOMode }) {
        imageOcclusionMode = undefined;
        await tick();
        imageOcclusionMode = options.mode;
        if (options.mode.kind === "add" && !("clonedNoteId" in options.mode)) {
            fieldStores[ioFields.image].set(options.html);
            // the image field is set programmatically and does not need debouncing
            // commit immediately to avoid a race condition with the occlusions field
            saveFieldNow();

            // new image is being added
            if (isIOImageLoaded) {
                resetIOImage(options.mode.imagePath, (event: ImageLoadedEvent) =>
                    onImageLoaded(
                        new CustomEvent("image-loaded", {
                            detail: event,
                        }),
                    ),
                );
            }
        }

        isIOImageLoaded = true;
    }

    function setImageField(html) {
        fieldStores[ioFields.image].set(html);
    }
    globalThis.setImageField = setImageField;

    function saveOcclusions(): void {
        if (isImageOcclusion && globalThis.canvas) {
            const occlusionsData = exportShapesToClozeDeletions($hideAllGuessOne);
            fieldStores[ioFields.occlusions].set(occlusionsData.clozes);
        }
    }

    // reset for new occlusion in add mode
    function resetIOImageLoaded() {
        isIOImageLoaded = false;
        globalThis.canvas.clear();
        globalThis.canvas = undefined;
        if (imageOcclusionMode?.kind === "add") {
            // canvas.clear indirectly calls saveOcclusions
            saveFieldNow();
            fieldStores[ioFields.image].set("");
        }
        const page = document.querySelector(".image-occlusion");
        if (page) {
            page.remove();
        }
    }
    globalThis.resetIOImageLoaded = resetIOImageLoaded;

    /** hide occlusions and image */
    function hideFieldInOcclusionType(
        index: number,
        ioFields: ImageOcclusionFieldIndexes,
    ) {
        if (isImageOcclusion) {
            if (index === ioFields.occlusions || index === ioFields.image) {
                return true;
            }
        }
        return false;
    }

    // Signal image occlusion image loading to Python
    function onImageLoaded(event: CustomEvent<ImageLoadedEvent>) {
        const detail = event.detail;
        bridgeCommand(
            `ioImageLoaded:${JSON.stringify(detail.path || detail.noteId?.toString())}`,
        );
    }

    // Signal editor UI state changes to add-ons

    let editorState: EditorState = EditorState.Initial;
    let lastEditorState: EditorState = editorState;

    function getEditorState(
        ioMaskEditorVisible: boolean,
        isImageOcclusion: boolean,
        isIOImageLoaded: boolean,
        imageOcclusionMode: IOMode | undefined,
    ): EditorState {
        if (isImageOcclusion && ioMaskEditorVisible && !isIOImageLoaded) {
            return EditorState.ImageOcclusionPicker;
        } else if (imageOcclusionMode && ioMaskEditorVisible) {
            return EditorState.ImageOcclusionMasks;
        } else if (!ioMaskEditorVisible && isImageOcclusion) {
            return EditorState.ImageOcclusionFields;
        }
        return EditorState.Fields;
    }

    function signalEditorState(newState: EditorState) {
        tick().then(() => {
            globalThis.editorState = newState;
            bridgeCommand(`editorState:${newState}:${lastEditorState}`);
            lastEditorState = newState;
        });
    }

    async function loadNote(nid: bigint, notetypeId: bigint, focusTo: number) {
        const notetype = await getNotetype({
            ntid: notetypeId,
        });
        const fieldNames = (
            await getFieldNames({
                ntid: notetype.id,
            })
        ).vals;
        const clozeFieldOrds = (await getClozeFieldOrds({ ntid: notetype.id })).ords;
        const clozeFields = fieldNames.map((name, index) =>
            clozeFieldOrds.includes(index),
        );
        if (mode === "add") {
            setNote(await newNote({ ntid: notetype.id }));
        } else {
            setNote(
                await getNote({
                    nid,
                }),
            );
        }
        const fieldValues = (
            await Promise.all(
                note!.fields.map((field) => encodeIriPaths({ val: field })),
            )
        ).map((field) => field.val);
        const tags = note!.tags;

        saveSession();
        setFields(fieldNames, fieldValues);
        setIsImageOcclusion(
            notetype.config?.originalStockKind ===
                StockNotetype_OriginalStockKind.IMAGE_OCCLUSION,
        );
        setNotetypeMeta(notetype);
        setCollapsed(notetype.fields.map((field) => field.config?.collapsed ?? false));
        setClozeFields(clozeFields);
        setPlainTexts(notetype.fields.map((field) => field.config?.plainText ?? false));
        setDescriptions(
            notetype.fields.map((field) => field.config?.description ?? ""),
        );
        // TODO: gui_hooks.editor_will_use_font_for_field
        setFonts(
            notetype.fields.map((field) => [
                field.config?.fontName ?? "",
                field.config?.fontSize ?? 16,
                field.config?.rtl ?? false,
            ]),
        );
        focusField(focusTo);
        // TODO: lastTextColor/lastHighlightColor profile config
        // setColorButtons(["#0000ff", "#0000ff"]);
        setTags(tags);
        // TODO: mw.pm.tags_collapsed()
        setTagsCollapsed(false);
        // TODO: renderMathjax col config
        setMathjaxEnabled(true);
        // TODO: shrinkEditorImages col config
        setShrinkImages(true);
        // TODO: closeHTMLTags col config
        setCloseHTMLTags(true);
        triggerChanges();
    }

    $: signalEditorState(editorState);

    $: editorState = getEditorState(
        $ioMaskEditorVisible,
        isImageOcclusion,
        isIOImageLoaded,
        imageOcclusionMode,
    );

    $: if (isImageOcclusion && $ioMaskEditorVisible && lastSavedTags) {
        setTags(lastSavedTags);
        lastSavedTags = null;
    }

    onMount(() => {
        function wrap(before: string, after: string): void {
            if (!$focusedInput || !editingInputIsRichText($focusedInput)) {
                return;
            }

            $focusedInput.element.then((element) => {
                wrapInternal(element, before, after, false);
            });
        }

        Object.assign(globalThis, {
            loadNote,
            saveSession,
            setFields,
            setCollapsed,
            setClozeFields,
            setPlainTexts,
            setDescriptions,
            setFonts,
            focusField,
            setTags,
            setTagsCollapsed,
            setBackgrounds,
            setClozeHint,
            saveNow,
            focusIfField,
            setNotetypeMeta,
            wrap,
            setMathjaxEnabled,
            setShrinkImages,
            setCloseHTMLTags,
            triggerChanges,
            setIsImageOcclusion,
            setupMaskEditor,
            saveOcclusions,
            setSticky,
            ...oldEditorAdapter,
        });

        editorState = getEditorState(
            $ioMaskEditorVisible,
            isImageOcclusion,
            isIOImageLoaded,
            imageOcclusionMode,
        );

        document.addEventListener("visibilitychange", saveOnPageHide);
        return () => document.removeEventListener("visibilitychange", saveOnPageHide);
    });

    let apiPartial: Partial<NoteEditorAPI> = {};
    export { apiPartial as api };

    const hoveredField: NoteEditorAPI["hoveredField"] = writable(null);
    const focusedField: NoteEditorAPI["focusedField"] = writable(null);
    const focusedInput: NoteEditorAPI["focusedInput"] = writable(null);

    const api: NoteEditorAPI = {
        ...apiPartial,
        hoveredField,
        focusedField,
        focusedInput,
        toolbar: toolbar as EditorToolbarAPI,
        fields,
    };

    setContextProperty(api);
    setupLifecycleHooks(api);

    $: tagAmount = $tags.length;

    let noteEditor: HTMLDivElement;

    export let uiResolve: (api: NoteEditorAPI) => void;
    export let mode: EditorMode;

    $: if (noteEditor) {
        uiResolve(api as NoteEditorAPI);
    }
</script>

<!--
@component
Serves as a pre-slotted convenience component which combines all the common
components and functionality for general note editing.
-->
<div class="note-editor" bind:this={noteEditor}>
    <EditorToolbar {size} {wrap} api={toolbar}>
        <svelte:fragment slot="notetypeButtons">
            {#if mode === "browse"}
                <ButtonGroupItem>
                    <PreviewButton />
                </ButtonGroupItem>
            {/if}
        </svelte:fragment>
    </EditorToolbar>

    {#if hint}
        <Absolute bottom right --margin="10px">
            <Notification>
                <Badge --badge-color="tomato" --icon-align="top">
                    <Icon icon={alertIcon} />
                </Badge>
                <span>{@html hint}</span>
            </Notification>
        </Absolute>
    {/if}

    {#if imageOcclusionMode && ($ioMaskEditorVisible || imageOcclusionMode?.kind === "add")}
        <div style="display: {$ioMaskEditorVisible ? 'block' : 'none'};">
            <ImageOcclusionPage
                mode={imageOcclusionMode}
                on:save={saveOcclusions}
                on:image-loaded={onImageLoaded}
            />
        </div>
    {/if}

    {#if $ioMaskEditorVisible && isImageOcclusion && !isIOImageLoaded}
        <ImageOcclusionPicker
            onPickImage={pickIOImage}
            onPickImageFromClipboard={pickIOImageFromClipboard}
        />
    {/if}

    {#if !$ioMaskEditorVisible}
        <Fields>
            {#each fieldsData as field, index}
                {@const content = fieldStores[index]}

                <EditorField
                    {field}
                    {content}
                    {index}
                    flipInputs={plainTextDefaults[index]}
                    api={fields[index]}
                    on:focusin={() => {
                        $focusedField = fields[index];
                        setAddonButtonsDisabled(false);
                        bridgeCommand(`focus:${index}`);
                    }}
                    on:focusout={() => {
                        $focusedField = null;
                        setAddonButtonsDisabled(true);
                        bridgeCommand(`blur:${index}`);
                        note!.fields[index] = transformContentBeforeSave(get(content));
                        updateCurrentNote();
                    }}
                    on:mouseenter={() => {
                        $hoveredField = fields[index];
                    }}
                    on:mouseleave={() => {
                        $hoveredField = null;
                    }}
                    collapsed={fieldsCollapsed[index]}
                    dupe={cols[index] === "dupe"}
                    --description-font-size="{field.fontSize}px"
                    --description-content={`"${field.description}"`}
                >
                    <svelte:fragment slot="field-label">
                        <LabelContainer
                            collapsed={fieldsCollapsed[index]}
                            on:toggle={() => toggleField(index)}
                            --icon-align="bottom"
                        >
                            <svelte:fragment slot="field-name">
                                <LabelName>
                                    {field.name}
                                </LabelName>
                            </svelte:fragment>
                            <FieldState>
                                {#if cols[index] === "dupe"}
                                    <DuplicateLink />
                                {/if}
                                {#if mode === "add"}
                                    <StickyBadge
                                        bind:active={stickies[index]}
                                        {index}
                                        show={fields[index] === $hoveredField ||
                                            fields[index] === $focusedField}
                                    />
                                {/if}
                                {#if plainTextDefaults[index]}
                                    <RichTextBadge
                                        show={!fieldsCollapsed[index] &&
                                            (fields[index] === $hoveredField ||
                                                fields[index] === $focusedField)}
                                        bind:off={richTextsHidden[index]}
                                        on:toggle={() => toggleRichTextInput(index)}
                                    />
                                {:else}
                                    <PlainTextBadge
                                        show={!fieldsCollapsed[index] &&
                                            (fields[index] === $hoveredField ||
                                                fields[index] === $focusedField)}
                                        bind:off={plainTextsHidden[index]}
                                        on:toggle={() => togglePlainTextInput(index)}
                                    />
                                {/if}
                            </FieldState>
                        </LabelContainer>
                    </svelte:fragment>
                    <svelte:fragment slot="rich-text-input">
                        <Collapsible
                            collapse={richTextsHidden[index]}
                            let:collapsed={hidden}
                            toggleDisplay
                        >
                            <RichTextInput
                                {hidden}
                                on:focusout={() => {
                                    saveFieldNow();
                                    $focusedInput = null;
                                }}
                                bind:this={richTextInputs[index]}
                                isClozeField={field.isClozeField}
                            />
                        </Collapsible>
                    </svelte:fragment>
                    <svelte:fragment slot="plain-text-input">
                        <Collapsible
                            collapse={plainTextsHidden[index]}
                            let:collapsed={hidden}
                            toggleDisplay
                        >
                            <PlainTextInput
                                {hidden}
                                fieldCollapsed={fieldsCollapsed[index]}
                                on:focusout={() => {
                                    saveFieldNow();
                                    $focusedInput = null;
                                }}
                                bind:this={plainTextInputs[index]}
                            />
                        </Collapsible>
                    </svelte:fragment>
                </EditorField>
            {/each}

            <MathjaxOverlay />
            <ImageOverlay maxWidth={250} maxHeight={125} />
        </Fields>

        <Shortcut
            keyCombination="Control+Shift+T"
            on:action={() => {
                updateTagsCollapsed(false);
            }}
        />
        <CollapseLabel
            collapsed={$tagsCollapsed}
            tooltip={$tagsCollapsed ? tr.editingExpand() : tr.editingCollapse()}
            on:toggle={() => updateTagsCollapsed(!$tagsCollapsed)}
        >
            {@html `${tagAmount > 0 ? tagAmount : ""} ${tr.editingTags()}`}
        </CollapseLabel>
        <Collapsible toggleDisplay collapse={$tagsCollapsed}>
            <TagEditor {tags} on:tagsupdate={saveTags} />
        </Collapsible>
    {/if}
</div>

<style lang="scss">
    .note-editor {
        display: flex;
        flex-direction: column;
        height: 100%;
    }

    :global(.image-occlusion) {
        position: fixed;
    }

    :global(.image-occlusion .tab-buttons) {
        display: none !important;
    }

    :global(.image-occlusion .top-tool-bar-container) {
        margin-left: 28px !important;
    }
    :global(.top-tool-bar-container .icon-button) {
        height: 36px !important;
        line-height: 1;
    }
    :global(.image-occlusion .tool-bar-container) {
        top: unset !important;
        margin-top: 2px !important;
    }
    :global(.image-occlusion .sticky-footer) {
        display: none;
    }
</style>
