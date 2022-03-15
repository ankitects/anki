<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script context="module" lang="ts">
    import type { Writable } from "svelte/store";

    import type { EditingInputAPI } from "./EditingArea.svelte";
    import type { EditorToolbarAPI } from "./editor-toolbar";
    import type { EditorFieldAPI } from "./EditorField.svelte";

    export interface NoteEditorAPI {
        fields: EditorFieldAPI[];
        focusedField: Writable<EditorFieldAPI | null>;
        focusedInput: Writable<EditingInputAPI | null>;
        toolbar: EditorToolbarAPI;
    }

    import { registerPackage } from "../lib/runtime-require";
    import contextProperty from "../sveltelib/context-property";
    import lifecycleHooks from "../sveltelib/lifecycle-hooks";

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
    import { onMount, tick } from "svelte";
    import { get, writable } from "svelte/store";

    import Absolute from "../components/Absolute.svelte";
    import Badge from "../components/Badge.svelte";
    import { bridgeCommand } from "../lib/bridgecommand";
    import { isApplePlatform } from "../lib/platform";
    import { ChangeTimer } from "./change-timer";
    import DecoratedElements from "./DecoratedElements.svelte";
    import { clearableArray } from "./destroyable";
    import DuplicateLink from "./DuplicateLink.svelte";
    import { EditorToolbar } from "./editor-toolbar";
    import type { FieldData } from "./EditorField.svelte";
    import EditorField from "./EditorField.svelte";
    import Fields from "./Fields.svelte";
    import FieldsEditor from "./FieldsEditor.svelte";
    import FrameElement from "./FrameElement.svelte";
    import { alertIcon } from "./icons";
    import { ImageHandle } from "./image-overlay";
    import { MathjaxHandle } from "./mathjax-overlay";
    import MathjaxElement from "./MathjaxElement.svelte";
    import Notification from "./Notification.svelte";
    import { PlainTextInput } from "./plain-text-input";
    import PlainTextBadge from "./PlainTextBadge.svelte";
    import { editingInputIsRichText, RichTextInput } from "./rich-text-input";
    import RichTextBadge from "./RichTextBadge.svelte";
    import { TagEditor } from "./tag-editor";

    function quoteFontFamily(fontFamily: string): string {
        // generic families (e.g. sans-serif) must not be quoted
        if (!/^[-a-z]+$/.test(fontFamily)) {
            fontFamily = `"${fontFamily}"`;
        }
        return fontFamily;
    }

    const size = isApplePlatform() ? 1.6 : 1.8;
    const wrap = true;

    const fieldStores: Writable<string>[] = [];
    let fieldNames: string[] = [];
    export function setFields(fs: [string, string][]): void {
        // this is a bit of a mess -- when moving to Rust calls, we should make
        // sure to have two backend endpoints for:
        // * the note, which can be set through this view
        // * the fieldname, font, etc., which cannot be set

        const newFieldNames: string[] = [];

        for (const [index, [fieldName]] of fs.entries()) {
            newFieldNames[index] = fieldName;
        }

        for (let i = fieldStores.length; i < newFieldNames.length; i++) {
            const newStore = writable("");
            fieldStores[i] = newStore;
            newStore.subscribe((value) => updateField(i, value));
        }

        for (
            let i = fieldStores.length;
            i > newFieldNames.length;
            i = fieldStores.length
        ) {
            fieldStores.pop();
        }

        for (const [index, [, fieldContent]] of fs.entries()) {
            fieldStores[index].set(fieldContent);
        }

        fieldNames = newFieldNames;
    }

    let fieldDescriptions: string[] = [];
    export function setDescriptions(fs: string[]): void {
        fieldDescriptions = fs;
    }

    let fonts: [string, number, boolean][] = [];
    let richTextsHidden: boolean[] = [];
    let plainTextsHidden: boolean[] = [];
    const fields = clearableArray<EditorFieldAPI>();

    export function setFonts(fs: [string, number, boolean][]): void {
        fonts = fs;

        richTextsHidden = fonts.map((_, index) => richTextsHidden[index] ?? false);
        plainTextsHidden = fonts.map((_, index) => plainTextsHidden[index] ?? true);
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

    let noteId: number | null = null;
    export function setNoteId(ntid: number): void {
        // TODO this is a hack, because it requires the NoteEditor to know implementation details of the PlainTextInput.
        // It should be refactored once we work on our own Undo stack
        for (const pi of plainTextInputs) {
            pi.api.codeMirror.editor.then((editor) => editor.clearHistory());
        }
        noteId = ntid;
    }

    function getNoteId(): number | null {
        return noteId;
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
        description: fieldDescriptions[index],
        fontFamily: quoteFontFamily(fonts[index][0]),
        fontSize: fonts[index][1],
        direction: fonts[index][2] ? "rtl" : "ltr",
    })) as FieldData[];

    function saveTags({ detail }: CustomEvent): void {
        bridgeCommand(`saveTags:${JSON.stringify(detail.tags)}`);
    }

    const fieldSave = new ChangeTimer();

    function updateField(index: number, content: string): void {
        fieldSave.schedule(
            () => bridgeCommand(`key:${index}:${getNoteId()}:${content}`),
            600,
        );
    }

    export function saveFieldNow(): void {
        /* this will always be a key save */
        fieldSave.fireImmediately();
    }

    export function saveOnPageHide() {
        if (document.visibilityState === "hidden") {
            // will fire on session close and minimize
            saveFieldNow();
        }
    }

    export function focusIfField(x: number, y: number): boolean {
        const elements = document.elementsFromPoint(x, y);
        const first = elements[0];

        if (first.shadowRoot) {
            const richTextInput = first.shadowRoot.lastElementChild! as HTMLElement;
            richTextInput.focus();
            return true;
        }

        return false;
    }

    let richTextInputs: RichTextInput[] = [];
    $: richTextInputs = richTextInputs.filter(Boolean);

    let plainTextInputs: PlainTextInput[] = [];
    $: plainTextInputs = plainTextInputs.filter(Boolean);

    const toolbar: Partial<EditorToolbarAPI> = {};

    import { wrapInternal } from "../lib/wrap";
    import * as oldEditorAdapter from "./old-editor-adapter";

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
            setFields,
            setDescriptions,
            setFonts,
            focusField,
            setTags,
            setBackgrounds,
            setClozeHint,
            saveNow: saveFieldNow,
            focusIfField,
            setNoteId,
            wrap,
            ...oldEditorAdapter,
        });

        document.addEventListener("visibilitychange", saveOnPageHide);
        return () => document.removeEventListener("visibilitychange", saveOnPageHide);
    });

    let apiPartial: Partial<NoteEditorAPI> = {};
    export { apiPartial as api };

    const focusedField: NoteEditorAPI["focusedField"] = writable(null);
    const focusedInput: NoteEditorAPI["focusedInput"] = writable(null);

    const api: NoteEditorAPI = {
        ...apiPartial,
        focusedField,
        focusedInput,
        toolbar: toolbar as EditorToolbarAPI,
        fields,
    };

    setContextProperty(api);
    setupLifecycleHooks(api);
</script>

<div class="note-editor">
    <FieldsEditor>
        <EditorToolbar {size} {wrap} api={toolbar}>
            <slot slot="notetypeButtons" name="notetypeButtons" />
        </EditorToolbar>

        {#if hint}
            <Absolute bottom right --margin="10px">
                <Notification>
                    <Badge --badge-color="tomato" --icon-align="top"
                        >{@html alertIcon}</Badge
                    >
                    <span>{@html hint}</span>
                </Notification>
            </Absolute>
        {/if}

        <Fields>
            <DecoratedElements>
                {#each fieldsData as field, index}
                    <EditorField
                        {field}
                        content={fieldStores[index]}
                        api={fields[index]}
                        on:focusin={() => {
                            $focusedField = fields[index];
                            bridgeCommand(`focus:${index}`);
                        }}
                        on:focusout={() => {
                            $focusedField = null;
                            bridgeCommand(
                                `blur:${index}:${getNoteId()}:${get(
                                    fieldStores[index],
                                )}`,
                            );
                        }}
                        --label-color={cols[index] === "dupe"
                            ? "var(--flag1-bg)"
                            : "transparent"}
                    >
                        <svelte:fragment slot="field-state">
                            {#if cols[index] === "dupe"}
                                <DuplicateLink />
                            {/if}
                            <RichTextBadge bind:off={richTextsHidden[index]} />
                            <PlainTextBadge bind:off={plainTextsHidden[index]} />

                            <slot name="field-state" {field} {index} />
                        </svelte:fragment>

                        <svelte:fragment slot="editing-inputs">
                            <RichTextInput
                                hidden={richTextsHidden[index]}
                                on:focusout={() => {
                                    saveFieldNow();
                                    $focusedInput = null;
                                }}
                                bind:this={richTextInputs[index]}
                            >
                                <ImageHandle />
                                <MathjaxHandle />
                            </RichTextInput>

                            <PlainTextInput
                                hidden={plainTextsHidden[index]}
                                on:focusout={() => {
                                    saveFieldNow();
                                    $focusedInput = null;
                                }}
                                bind:this={plainTextInputs[index]}
                            />
                        </svelte:fragment>
                    </EditorField>
                {/each}

                <MathjaxElement />
                <FrameElement />
            </DecoratedElements>
        </Fields>
    </FieldsEditor>

    <TagEditor {tags} on:tagsupdate={saveTags} />
</div>

<style lang="scss">
    .note-editor {
        height: 100%;
        display: flex;
        flex-direction: column;
    }
</style>
