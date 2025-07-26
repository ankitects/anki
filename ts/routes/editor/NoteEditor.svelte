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
    import { EditorState, type EditorMode } from "./types";
    import { ContextMenu, Item } from "$lib/context-menu";
    import type Modal from "bootstrap/js/dist/modal";
    import { getContext } from "svelte";
    import { modalsKey } from "$lib/components/context-keys";

    export interface NoteEditorAPI {
        fields: EditorFieldAPI[];
        hoveredField: Writable<EditorFieldAPI | null>;
        focusedField: Writable<EditorFieldAPI | null>;
        focusedInput: Writable<EditingInputAPI | null>;
        toolbar: EditorToolbarAPI;
        state: Writable<EditorState>;
        lastIOImagePath: Writable<string | null>;
    }

    import { registerPackage } from "@tslib/runtime-require";
    import {
        filenameToLink,
        openFilePickerForImageOcclusion,
        readImageFromClipboard,
        extractImagePathFromHtml,
        extractImagePathFromData,
    } from "./rich-text-input/data-transfer";
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
    import { onDestroy, onMount, tick } from "svelte";
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
    import type { HistoryEntry, NotetypeIdAndModTime, SessionOptions } from "./types";

    let contextMenu: ContextMenu;
    const [onContextMenu, contextMenuItems] = setupContextMenu();
    let contextMenuInput: EditingInputAPI | null = null;

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

    //  QFont returns "Kozuka Gothic Pro L" but WebEngine expects "Kozuka Gothic Pro Light"
    // - there may be other cases like a trailing 'Bold' that need fixing, but will
    // wait for further reports first.
    function mungeFontName(fontName: string): string {
        return fontName.replace(/ L$/g, " Light");
    }

    export function setFonts(fs: [string, number, boolean][]): void {
        fonts = fs;
    }

    let stickies: boolean[] = [];

    function setSticky(stckies: boolean[]): void {
        stickies = stckies;
    }

    async function toggleStickyAll() {
        if (isLegacy) {
            bridgeCommand(
                "toggleStickyAll",
                (values: boolean[]) => (stickies = values),
            );
        } else {
            const values: boolean[] = [];
            const notetype = await getNotetype({ ntid: notetypeMeta.id });
            const anySticky = notetype.fields.some((f) => f.config!.sticky);
            for (const field of notetype.fields) {
                const sticky = field.config!.sticky;
                if (!anySticky || sticky) {
                    field.config!.sticky = !sticky;
                }
                values.push(field.config!.sticky);
            }
            await updateEditorNotetype(notetype);
            setSticky(values);
        }
    }

    let deregisterSticky: () => void;

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
    $: tagsCollapsedMetaKey = `${mode}TagsCollapsed`;

    export function setTagsCollapsed(collapsed: boolean): void {
        $tagsCollapsed = collapsed;
    }

    async function updateTagsCollapsed(collapsed: boolean) {
        $tagsCollapsed = collapsed;
        await setMeta(tagsCollapsedMetaKey, collapsed);
    }

    function clearCodeMirrorHistory() {
        // TODO this is a hack, because it requires the NoteEditor to know implementation details of the PlainTextInput.
        // It should be refactored once we work on our own Undo stack
        for (const pi of plainTextInputs) {
            pi.api.codeMirror.editor.then((editor) => editor.clearHistory());
        }
    }

    let noteId: number | null = null;
    export function setNoteId(ntid: number): void {
        clearCodeMirrorHistory();
        noteId = ntid;
    }

    function getNoteId(): number | null {
        return noteId;
    }

    let note: Note;
    export function setNote(n: Note): void {
        note = n;
        clearCodeMirrorHistory();
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
        note!.tags = detail.tags;
        bridgeCommand("saveTags");
        updateCurrentNote();
    }

    const fieldSave = new ChangeTimer();

    async function transformContentBeforeSave(content: string): Promise<string> {
        content = content.replace(/ data-editor-shrink="(true|false)"/g, "");
        if (!isLegacy) {
            // misbehaving apps may include a null byte in the text
            content = content.replaceAll("\0", "");
            // reverse the url quoting we added to get images to display
            content = (await decodeIriPaths({ val: content })).val;

            if (["<br>", "<div><br></div>"].includes(content)) {
                return "";
            }
        }
        return content;
    }

    async function updateCurrentNote() {
        if (mode !== "add") {
            await updateEditorNote({
                notes: [note!],
                skipUndoEntry: false,
            });
        }
    }

    async function updateField(index: number, content: string): Promise<void> {
        fieldSave.schedule(async () => {
            if (isLegacy) {
                bridgeCommand(
                    `key:${index}:${getNoteId()}:${await transformContentBeforeSave(
                        content,
                    )}`,
                );
            } else {
                bridgeCommand(`key:${index}`);
                note!.fields[index] = await transformContentBeforeSave(content);
                await updateCurrentNote();
                await updateDuplicateDisplay();
            }
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

    // Used for detecting changed sticky fields on close
    let lastAddedNote: Note | null = null;

    async function shouldPromptBeforeClosing(): Promise<boolean> {
        const brPattern = /<br\s*\/?>/gi;
        for (let c = 0; c < note.fields.length; c++) {
            const field = note.fields[c].replace(brPattern, "").trim();
            const notChangedValues = new Set(["", "<br>"]);
            if (lastAddedNote && stickies[c]) {
                const previousFieldValue = lastAddedNote.fields[c]
                    .replace(brPattern, "")
                    .trim();
                notChangedValues.add(previousFieldValue);
            }
            if (!notChangedValues.has(field)) {
                return true;
            }
        }

        return false;
    }

    async function closeAddCards() {
        saveNow();
        await closeAddCardsBackend({ val: await shouldPromptBeforeClosing() });
    }

    async function closeEditCurrent() {
        saveNow();
        await closeEditCurrentBackend({});
    }

    async function onClose() {
        if (mode === "add") {
            await closeAddCards();
        } else if (mode == "current") {
            await closeEditCurrent();
        }
    }

    async function onAdd() {
        // TODO get selected deck
        await addCurrentNote(1n);
    }

    const modals = getContext<Map<string, Modal>>(modalsKey);
    let modalKey: string;

    let history: HistoryEntry[] = [];

    export async function addNoteToHistory(note: Note) {
        let text = (
            await htmlToTextLine({
                text: note.fields.join(", "),
                preserveMediaFilenames: true,
            })
        ).val;
        if (text.length > 30) {
            text = `${text.slice(0, 30)}...`;
        }
        history = [
            ...history,
            {
                text,
                noteId: note.id,
            },
        ];
    }

    export function onHistory() {
        modals.get(modalKey)!.show();
    }

    export function saveOnPageHide() {
        if (document.visibilityState === "hidden") {
            // will fire on session close and minimize
            saveFieldNow();
        }
    }

    async function updateDuplicateDisplay(): Promise<void> {
        if (!note) {
            return;
        }
        const result = await noteFieldsCheck(note);
        const cols = new Array(note.fields.length).fill("");
        if (result.state === NoteFieldsCheckResponse_State.DUPLICATE) {
            cols[0] = "dupe";
        } else if (result.state === NoteFieldsCheckResponse_State.NOTETYPE_NOT_CLOZE) {
            hint = tr.addingClozeOutsideClozeNotetype();
        } else if (result.state === NoteFieldsCheckResponse_State.FIELD_NOT_CLOZE) {
            hint = tr.addingClozeOutsideClozeField();
        }
        setBackgrounds(cols);
        setClozeHint(hint);
    }

    async function loadNewNote() {
        await loadNote(0n, notetypeMeta.id, 0, null);
    }

    async function noteCanBeAdded(): Promise<boolean> {
        let problem: string | null = null;
        const result = await noteFieldsCheck(note!);
        if (result.state === NoteFieldsCheckResponse_State.EMPTY) {
            if (isImageOcclusion) {
                problem = tr.notetypesNoOcclusionCreated2();
            } else {
                problem = tr.addingTheFirstFieldIsEmpty();
            }
        }
        if (result.state === NoteFieldsCheckResponse_State.MISSING_CLOZE) {
            // TODO: askUser(tr.addingYouHaveAClozeDeletionNote())
            return false;
        }
        if (result.state === NoteFieldsCheckResponse_State.NOTETYPE_NOT_CLOZE) {
            problem = tr.addingClozeOutsideClozeNotetype();
        }
        if (result.state === NoteFieldsCheckResponse_State.FIELD_NOT_CLOZE) {
            problem = tr.addingClozeOutsideClozeField();
        }
        return problem ? false : true;
    }

    async function addCurrentNoteInner(deckId: bigint) {
        if (!(await noteCanBeAdded())) {
            return;
        }
        const noteId = (
            await addEditorNote({
                note: note!,
                deckId,
            })
        ).noteId;
        note.id = noteId;
        addNoteToHistory(note!);
        lastAddedNote = note;
        await loadNewNote();
    }

    export async function addCurrentNote(deckId: bigint) {
        if (mode !== "add") {
            return;
        }
        if (isImageOcclusion) {
            saveOcclusions();
            await addCurrentNoteInner(deckId);
            resetIOImageLoaded();
        } else {
            await addCurrentNoteInner(deckId);
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

    export function getNoteInfo() {
        return {
            id: note.id.toString() ?? null,
            mid: notetypeMeta.id.toString(),
            fields: note.fields ?? [],
        };
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

    import {
        Notetype_Config_Kind,
        StockNotetype_OriginalStockKind,
    } from "@generated/anki/notetypes_pb";
    import type { Notetype } from "@generated/anki/notetypes_pb";
    import {
        getFieldNames,
        getClozeFieldOrds,
        getImageOcclusionFields,
        getNote,
        getNotetype,
        encodeIriPaths,
        newNote,
        updateEditorNote,
        decodeIriPaths,
        noteFieldsCheck,
        addEditorNote,
        addMediaFromPath,
        updateEditorNotetype,
        closeAddCards as closeAddCardsBackend,
        closeEditCurrent as closeEditCurrentBackend,
        htmlToTextLine,
    } from "@generated/backend";
    import { wrapInternal } from "@tslib/wrap";
    import { getProfileConfig, getMeta, setMeta, getColConfig } from "@tslib/profile";
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
    import { NoteFieldsCheckResponse_State, type Note } from "@generated/anki/notes_pb";
    import { setupContextMenu } from "./context-menu.svelte";
    import { registerShortcut } from "@tslib/shortcuts";
    import ActionButtons from "./ActionButtons.svelte";
    import HistoryModal from "./HistoryModal.svelte";

    $: isIOImageLoaded = false;
    $: ioImageLoadedStore.set(isIOImageLoaded);
    let imageOcclusionMode: IOMode | undefined;
    let ioFields = new ImageOcclusionFieldIndexes({});
    const lastIOImagePath: Writable<string | null> = writable(null);

    async function pickIOImage() {
        imageOcclusionMode = undefined;
        if (isLegacy) {
            bridgeCommand("addImageForOcclusion");
        } else {
            const filename = await openFilePickerForImageOcclusion();
            if (!filename) {
                return;
            }
            setupMaskEditor(filename);
        }
    }

    async function pickIOImageFromClipboard() {
        imageOcclusionMode = undefined;
        if (isLegacy) {
            bridgeCommand("addImageForOcclusionFromClipboard");
        } else {
            await setupMaskEditorFromClipboard();
        }
    }

    async function handlePickerDrop(event: DragEvent) {
        if ($editorState === EditorState.ImageOcclusionPicker) {
            const path = await extractImagePathFromData(event.dataTransfer!);
            if (path) {
                setupMaskEditor(path);
                event.preventDefault();
            }
        }
    }

    async function setupMaskEditor(filename: string) {
        if (mode == "add") {
            setupMaskEditorForNewNote(filename);
        } else {
            setupMaskEditorForExistingNote(filename);
        }
    }
    async function setupMaskEditorInner(options: { html: string; mode: IOMode }) {
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

    async function setupMaskEditorForNewNote(imagePath: string) {
        const imageFieldHtml = filenameToLink(
            (
                await addMediaFromPath({
                    path: imagePath,
                })
            ).val,
        );
        $lastIOImagePath = await extractImagePathFromHtml(imageFieldHtml);
        setupMaskEditorInner({
            html: imageFieldHtml,
            mode: {
                kind: "add",
                imagePath: imagePath,
                notetypeId: notetypeMeta.id,
            },
        });
    }

    async function setupMaskEditorForExistingNote(imagePath: string | null = null) {
        if (imagePath) {
            const imageFieldHtml = filenameToLink(
                (
                    await addMediaFromPath({
                        path: imagePath,
                    })
                ).val,
            );
            $lastIOImagePath = await extractImagePathFromHtml(imageFieldHtml);
            resetIOImage(imagePath, () => {});
            setImageField(imageFieldHtml);
        }
        setupMaskEditorInner({
            html: note!.fields[ioFields.image],
            mode: {
                kind: "edit",
                noteId: note!.id,
            },
        });
    }

    async function setupMaskEditorFromClipboard() {
        const path = await readImageFromClipboard();
        if (path) {
            setupMaskEditor(path);
        } else {
            alert(tr.editingNoImageFoundOnClipboard());
        }
    }

    function setImageField(html) {
        fieldStores[ioFields.image].set(html);
    }

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

    const editorState: Writable<EditorState> = writable(EditorState.Initial);
    let lastEditorState: EditorState = $editorState;

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

    async function loadNote(
        nid: bigint | null,
        notetypeId: bigint,
        focusTo: number,
        originalNoteId: bigint | null,
    ): Promise<bigint> {
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
                    nid: nid!,
                }),
            );
        }
        if (originalNoteId) {
            const originalNote = await getNote({
                nid: originalNoteId,
            });
            note!.fields = originalNote.fields;
            note!.tags = originalNote.tags;
        }
        const fieldValues = (
            await Promise.all(
                note!.fields.map((field) => encodeIriPaths({ val: field })),
            )
        ).map((field) => field.val);
        const tags = note!.tags;
        const lastTextColor = (await getProfileConfig("lastTextColor")) ?? "#0000ff";
        const lastHighlightColor =
            (await getProfileConfig("lastHighlightColor")) ?? "#0000ff";

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
        setFonts(
            notetype.fields.map((field) => [
                mungeFontName(field.config?.fontName ?? ""),
                field.config?.fontSize ?? 16,
                field.config?.rtl ?? false,
            ]),
        );
        focusField(focusTo);
        toolbar.inlineButtons?.setColorButtons([lastTextColor, lastHighlightColor]);
        await toolbar.toolbar?.setShown(
            "image-occlusion-button",
            notetype.config?.originalStockKind ===
                StockNotetype_OriginalStockKind.IMAGE_OCCLUSION,
        );
        await toolbar.toolbar?.setShown(
            "cloze",
            notetype.config?.kind === Notetype_Config_Kind.CLOZE,
        );
        setTags(tags);
        setTagsCollapsed(await getMeta(tagsCollapsedMetaKey));
        setMathjaxEnabled((await getColConfig("renderMathjax")) ?? true);
        setShrinkImages((await getColConfig("shrinkEditorImages")) ?? true);
        setCloseHTMLTags((await getColConfig("closeHTMLTags")) ?? true);
        if (mode === "add") {
            setSticky(notetype.fields.map((field) => field.config?.sticky ?? false));
        }
        if (isImageOcclusion) {
            const imageField = note!.fields[ioFields.image];
            $lastIOImagePath = await extractImagePathFromHtml(imageField);
            if (mode !== "add") {
                setupMaskEditorInner({
                    html: imageField,
                    mode: {
                        kind: "edit",
                        noteId: nid!,
                    },
                });
            } else if (originalNoteId) {
                setupMaskEditorInner({
                    html: imageField,
                    mode: {
                        kind: "add",
                        clonedNoteId: originalNoteId,
                    },
                });
            }
        }
        await updateDuplicateDisplay();
        triggerChanges();

        return note!.id;
    }

    async function reloadNote() {
        await loadNote(note!.id, notetypeMeta.id, 0, null);
    }

    function checkNonLegacy(value: any): any | undefined {
        if (isLegacy) {
            return value;
        }
        return undefined;
    }

    function preventDefaultIfNonLegacy(event: Event) {
        if (!isLegacy) {
            event.preventDefault();
        }
    }

    $: signalEditorState($editorState);

    $: $editorState = getEditorState(
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
        if (mode === "add") {
            deregisterSticky = registerShortcut(toggleStickyAll, "Shift+F9");
        }

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
            reloadNote,
            saveSession,
            setFields,
            setCollapsed,
            setClozeFields,
            setPlainTexts,
            setDescriptions,
            setFonts,
            getNoteInfo,
            focusField,
            setTags,
            setTagsCollapsed,
            setBackgrounds,
            setClozeHint,
            saveNow,
            closeAddCards,
            focusIfField,
            getNoteId,
            setNoteId,
            setNotetypeMeta,
            wrap,
            setMathjaxEnabled,
            setShrinkImages,
            setCloseHTMLTags,
            triggerChanges,
            setIsImageOcclusion,
            setupMaskEditor,
            setupMaskEditorForNewNote,
            setupMaskEditorForExistingNote,
            setImageField,
            resetIOImageLoaded,
            saveOcclusions,
            setSticky,
            ...oldEditorAdapter,
        });

        $editorState = getEditorState(
            $ioMaskEditorVisible,
            isImageOcclusion,
            isIOImageLoaded,
            imageOcclusionMode,
        );

        document.addEventListener("visibilitychange", saveOnPageHide);
        return () => document.removeEventListener("visibilitychange", saveOnPageHide);
    });

    onDestroy(() => {
        deregisterSticky();
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
        state: editorState,
        lastIOImagePath,
    };

    setContextProperty(api);
    setupLifecycleHooks(api);

    $: tagAmount = $tags.length;

    let noteEditor: HTMLDivElement;

    export let uiResolve: (api: NoteEditorAPI) => void;
    export let mode: EditorMode;
    export let isLegacy: boolean;

    $: if (noteEditor) {
        uiResolve(api as NoteEditorAPI);
    }
</script>

<!-- Block Qt's default drag & drop behavior -->
<svelte:body
    on:dragenter={preventDefaultIfNonLegacy}
    on:dragover={preventDefaultIfNonLegacy}
    on:drop={preventDefaultIfNonLegacy}
/>

<!--
@component
Serves as a pre-slotted convenience component which combines all the common
components and functionality for general note editing.
-->
<div
    class="note-editor"
    role="presentation"
    bind:this={noteEditor}
    on:contextmenu={(event) => {
        if (!isLegacy) {
            contextMenuInput = $focusedInput;
            onContextMenu(event, api, $focusedInput, contextMenu);
        }
    }}
    on:dragover={preventDefaultIfNonLegacy}
    on:drop={checkNonLegacy(handlePickerDrop)}
>
    <EditorToolbar {size} {wrap} api={toolbar}>
        <svelte:fragment slot="notetypeButtons">
            {#if mode === "browser"}
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
                    on:focusout={async () => {
                        $focusedField = null;
                        setAddonButtonsDisabled(true);
                        if (isLegacy) {
                            bridgeCommand(
                                `blur:${index}:${getNoteId()}:${await transformContentBeforeSave(
                                    get(content),
                                )}`,
                            );
                        } else {
                            bridgeCommand(`blur:${index}`);
                            note!.fields[index] = await transformContentBeforeSave(
                                get(content),
                            );
                            await updateCurrentNote();
                            await updateDuplicateDisplay();
                        }
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
                                    <DuplicateLink {note} />
                                {/if}
                                {#if mode === "add"}
                                    <StickyBadge
                                        bind:active={stickies[index]}
                                        {index}
                                        {note}
                                        {isLegacy}
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
                                {isLegacy}
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

    {#if !isLegacy}
        <ActionButtons {mode} {onClose} {onAdd} {onHistory} {history} />
        <HistoryModal bind:modalKey {history} />
    {/if}

    {#if !isLegacy}
        <ContextMenu bind:this={contextMenu}>
            {#each contextMenuItems as item}
                <Item
                    click={() => {
                        item.action();
                        contextMenuInput?.focus();
                    }}
                >
                    {item.label}
                </Item>
            {/each}
        </ContextMenu>
    {/if}
</div>

<style lang="scss">
    .note-editor {
        display: flex;
        flex-direction: column;
        height: 100%;
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
