<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script context="module" lang="ts">
    import type { Writable } from "svelte/store";

    import type { HooksAPI } from "../lib/hooks";
    import type { Notetypes } from "../lib/proto";
    import type { EditingInputAPI } from "./EditingArea.svelte";
    import type { EditorToolbarAPI } from "./editor-toolbar";
    import type { EditorFieldAPI } from "./EditorField.svelte";
    import type { TagEditorAPI } from "./tag-editor";

    interface OnLoadNoteInput {
        fields: Notetypes.Notetype.Field[];
        contents: string[];
    }

    export interface NoteEditorAPI {
        focusedField: Writable<EditorFieldAPI | null>;
        focusedInput: Writable<EditingInputAPI | null>;
        toolbar: EditorToolbarAPI;
        fields: EditorFieldAPI[];
        tagEditor: TagEditorAPI;
        onLoadNote: HooksAPI<OnLoadNoteInput>;
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
    import { createEventDispatcher, onMount } from "svelte";
    import { writable } from "svelte/store";

    import Absolute from "../components/Absolute.svelte";
    import Badge from "../components/Badge.svelte";
    import { bridgeCommand } from "../lib/bridgecommand";
    import { noop } from "../lib/functional";
    import { hooks } from "../lib/hooks";
    import type { Callback } from "../lib/typing";
    import { ChangeTimer } from "./change-timer";
    import DecoratedElements from "./DecoratedElements.svelte";
    import { clearableArray } from "./destroyable";
    import DuplicateLink from "./DuplicateLink.svelte";
    import { ClozeButtons, EditorToolbar } from "./editor-toolbar";
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

    export let fields: Notetypes.Notetype.Field[];
    export let contents: string[];
    export let tags: string[];

    const size = 1.6;
    const wrap = true;

    let fieldsData: [Notetypes.Notetype.Field, string][] = [];
    const tagsStore = writable<string[]>([]);

    let richTextsHidden: boolean[] = [];
    let plainTextsHidden: boolean[] = [];

    function zip<T, U>(first: T[], second: U[]): [T, U][] {
        return first.map((t: T, index: number) => [t, second[index]]);
    }

    const [onLoadNote, runLoadNote] = hooks<OnLoadNoteInput>();

    function loadEditor(
        fields: Notetypes.Notetype.Field[],
        contents: string[],
        tags: string[],
    ): void {
        // For now we won't await the hook
        runLoadNote({ fields, contents });

        fieldsData = zip(fields, contents);
        $tagsStore = tags;

        richTextsHidden = contents.map((_, index) => richTextsHidden[index] ?? false);
        plainTextsHidden = contents.map((_, index) => plainTextsHidden[index] ?? true);

        $focusedInput?.refocus();
    }

    $: if (fields && contents && tags) {
        loadEditor(fields, contents, tags);
    }

    const fieldsArray = clearableArray<EditorFieldAPI>();

    async function focusField(index: number | null): Promise<void> {
        if (typeof index === "number") {
            if (!(index in fieldsArray)) {
                return;
            }

            fieldsArray[index].editingArea?.refocus();
        } else {
            $focusedInput?.refocus();
        }
    }

    let cols: ("dupe" | "")[] = [];
    function setBackgrounds(cls: ("dupe" | "")[]): void {
        cols = cls;
    }

    let hint: string = "";
    function setClozeHint(hnt: string): void {
        hint = hnt;
    }

    function saveTags({ detail }: CustomEvent): void {
        bridgeCommand(`saveTags:${JSON.stringify(detail.tags)}`);
    }

    const fieldSave = new ChangeTimer();
    let fieldBlur: Callback = noop;

    function updateField(index: number, content: string): void {
        fieldSave.schedule(() => {
            // This correctly updates the stached content,
            // so that fieldsData matches with the content store.
            fieldsData[index][1] = content;
            fieldsData = fieldsData;

            bridgeCommand(`key:${index}:${globalThis.getNoteId()}:${content}`);
            dispatch("contentupdate", { index, content });
        }, 600);

        fieldBlur = () =>
            bridgeCommand(
                `blur:${index}:${globalThis.getNoteId()}:${fieldsData[index][1]}`,
            );
    }

    const dispatch = createEventDispatcher();

    function saveFieldNow(): void {
        /* this will always be a key save */
        fieldSave.fireImmediately();
    }

    function saveOnPageHide() {
        if (document.visibilityState === "hidden") {
            // will fire on session close and minimize
            saveFieldNow();
        }
    }

    function focusIfField(x: number, y: number): boolean {
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

    function resetPlainTextHistory(): void {
        for (const pi of plainTextInputs) {
            pi.api?.codeMirror.editor.then((editor) => editor.clearHistory());
        }
    }

    const toolbar: Partial<EditorToolbarAPI> = {};
    const tagEditor: Partial<TagEditorAPI> = {};

    let isCloze: boolean;
    $: isCloze = true; //notetype?.config!.kind === Notetypes.Notetype.Config.Kind.KIND_CLOZE;

    import { wrapInternal } from "../lib/wrap";
    import * as oldEditorAdapter from "./old-editor-adapter";

    Object.assign(globalThis, {
        focusField,
        setBackgrounds,
        setClozeHint,
        saveNow: saveFieldNow,
        resetPlainTextHistory,
        focusIfField,
        ...oldEditorAdapter,
    });

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
            wrap,
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
        fields: fieldsArray,
        tagEditor: tagEditor as TagEditorAPI,
        onLoadNote,
    };

    setContextProperty(api);
    setupLifecycleHooks(api);
</script>

<!--
@component
Serves as a pre-slotted convenience component which combines all the common
components and functionality for general note editing.

Functionality exclusive to specifc note-editing views (e.g. in the browser or
the AddCards dialog) should be implemented in the user of this component.
-->
<div class="note-editor">
    <FieldsEditor>
        <EditorToolbar {size} {wrap} api={toolbar}>
            <slot slot="notetypeButtons" name="notetypeButtons" />

            <svelte:fragment slot="extraButtonGroups">
                {#if isCloze}
                    <ClozeButtons />
                {/if}
            </svelte:fragment>
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
                {#each fieldsData as [field, content], index}
                    <EditorField
                        {field}
                        {content}
                        api={fieldsArray[index]}
                        on:focusin={() => {
                            $focusedField = fieldsArray[index];
                            bridgeCommand(`focus:${index}`);
                        }}
                        on:focusout={() => {
                            $focusedField = null;
                            fieldBlur();
                        }}
                        on:contentupdate={({ detail: content }) =>
                            updateField(index, content)}
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

    <TagEditor tags={tagsStore} api={tagEditor} on:tagsupdate={saveTags} />
</div>

<style lang="scss">
    .note-editor {
        height: 100%;
        display: flex;
        flex-direction: column;
    }
</style>
