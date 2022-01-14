<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script context="module" lang="ts">
    import type { EditorFieldAPI } from "./EditorField.svelte";
    import type { RichTextInputAPI } from "./RichTextInput.svelte";
    import type { PlainTextInputAPI } from "./PlainTextInput.svelte";
    import type { EditorToolbarAPI } from "./EditorToolbar.svelte";

    import contextProperty from "../sveltelib/context-property";
    import { writable, get } from "svelte/store";

    export interface NoteEditorAPI {
        fields: EditorFieldAPI[];
        currentField: Writable<EditorFieldAPI | null>;
        activeInput: Writable<RichTextInputAPI | PlainTextInputAPI | null>;
        focusInRichText: Writable<boolean>;
        toolbar: EditorToolbarAPI;
    }

    const key = Symbol("noteEditor");
    const [set, getNoteEditor, hasNoteEditor] = contextProperty<NoteEditorAPI>(key);

    export { getNoteEditor, hasNoteEditor };

    const activeInput = writable<RichTextInputAPI | PlainTextInputAPI | null>(null);
    const currentField = writable<EditorFieldAPI | null>(null);
</script>

<script lang="ts">
    import NoteEditor from "./NoteEditor.svelte";
    import FieldsEditor from "./FieldsEditor.svelte";
    import Fields from "./Fields.svelte";
    import EditorField from "./EditorField.svelte";
    import type { FieldData } from "./EditorField.svelte";
    import TagEditor from "./TagEditor.svelte";

    import EditorToolbar from "./EditorToolbar.svelte";
    import Notification from "./Notification.svelte";
    import Absolute from "../components/Absolute.svelte";
    import Badge from "../components/Badge.svelte";
    import DuplicateLink from "./DuplicateLink.svelte";

    import DecoratedElements from "./DecoratedElements.svelte";
    import RichTextInput from "./RichTextInput.svelte";
    import { MathjaxHandle } from "./mathjax-overlay";
    import { ImageHandle } from "./image-overlay";
    import PlainTextInput from "./PlainTextInput.svelte";
    import MathjaxElement from "./MathjaxElement.svelte";
    import FrameElement from "./FrameElement.svelte";

    import RichTextBadge from "./RichTextBadge.svelte";
    import PlainTextBadge from "./PlainTextBadge.svelte";
    import StickyBadge from "./StickyBadge.svelte";

    import { onMount, onDestroy } from "svelte";
    import type { Writable } from "svelte/store";
    import { bridgeCommand } from "../lib/bridgecommand";
    import { registerShortcut } from "../lib/shortcuts";
    import { isApplePlatform } from "../lib/platform";
    import { ChangeTimer } from "./change-timer";
    import { alertIcon } from "./icons";
    import { clearableArray } from "./destroyable";

    function quoteFontFamily(fontFamily: string): string {
        // generic families (e.g. sans-serif) must not be quoted
        if (!/^[-a-z]+$/.test(fontFamily)) {
            fontFamily = `"${fontFamily}"`;
        }
        return fontFamily;
    }

    let size = isApplePlatform() ? 1.6 : 1.8;
    let wrap = true;

    let fieldStores: Writable<string>[] = [];
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

        for (const [index, [_, fieldContent]] of fs.entries()) {
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
    let fields = clearableArray<EditorFieldAPI>();

    export function setFonts(fs: [string, number, boolean][]): void {
        fonts = fs;

        richTextsHidden = fonts.map((_, index) => richTextsHidden[index] ?? false);
        plainTextsHidden = fonts.map((_, index) => plainTextsHidden[index] ?? true);
    }

    let focusTo: number = 0;
    export function focusField(n: number): void {
        if (typeof n === "number") {
            focusTo = n;
            fields[focusTo].editingArea?.refocus();
        }
    }

    let textColor: string = "black";
    let highlightColor: string = "black";
    export function setColorButtons([textClr, highlightClr]: [string, string]): void {
        textColor = textClr;
        highlightColor = highlightClr;
    }

    let tags = writable<string[]>([]);
    export function setTags(ts: string[]): void {
        $tags = ts;
    }

    let stickies: boolean[] | null = null;
    export function setSticky(sts: boolean[]): void {
        stickies = sts;
    }

    let noteId: number | null = null;
    export function setNoteId(ntid: number): void {
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

    function toggleStickyAll(): void {
        bridgeCommand("toggleStickyAll", (values: boolean[]) => (stickies = values));
    }

    let deregisterSticky: () => void;
    export function activateStickyShortcuts() {
        deregisterSticky = registerShortcut(toggleStickyAll, "Shift+F9");
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

    const focusInRichText = writable<boolean>(false);

    let toolbar: Partial<EditorToolbarAPI> = {};

    export let api = {};

    Object.assign(
        api,
        set({
            currentField,
            activeInput,
            focusInRichText,
            toolbar: toolbar as EditorToolbarAPI,
            fields,
        }),
    );

    import { wrapInternal } from "../lib/wrap";

    onMount(() => {
        function wrap(before: string, after: string): void {
            if (!get(focusInRichText)) {
                return;
            }

            const input = get(activeInput!) as RichTextInputAPI;

            input.element.then((element) => {
                wrapInternal(element, before, after, false);
            });
        }

        Object.assign(globalThis, {
            setFields,
            setDescriptions,
            setFonts,
            focusField,
            setColorButtons,
            setTags,
            setSticky,
            setBackgrounds,
            setClozeHint,
            saveNow: saveFieldNow,
            activateStickyShortcuts,
            focusIfField,
            setNoteId,
            wrap,
        });

        document.addEventListener("visibilitychange", saveOnPageHide);
        return () => document.removeEventListener("visibilitychange", saveOnPageHide);
    });

    onDestroy(() => deregisterSticky);
</script>

<NoteEditor>
    <FieldsEditor>
        <EditorToolbar {size} {wrap} {textColor} {highlightColor} api={toolbar} />

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
                        autofocus={index === focusTo}
                        api={fields[index]}
                        on:focusin={() => {
                            $currentField = fields[index];
                            bridgeCommand(`focus:${index}`);
                        }}
                        on:focusout={() => {
                            $currentField = null;
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
                            {#if stickies}
                                <StickyBadge active={stickies[index]} {index} />
                            {/if}
                        </svelte:fragment>

                        <svelte:fragment slot="editing-inputs">
                            <RichTextInput
                                hidden={richTextsHidden[index]}
                                on:focusin={() => {
                                    $focusInRichText = true;
                                    $activeInput = richTextInputs[index].api;
                                }}
                                on:focusout={() => {
                                    $focusInRichText = false;
                                    $activeInput = null;
                                    saveFieldNow();
                                }}
                                bind:this={richTextInputs[index]}
                            >
                                <ImageHandle />
                                <MathjaxHandle />
                            </RichTextInput>

                            <PlainTextInput
                                hidden={plainTextsHidden[index]}
                                on:focusin={() => {
                                    $activeInput = plainTextInputs[index].api;
                                }}
                                on:focusout={() => {
                                    $activeInput = null;
                                    saveFieldNow();
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

    <TagEditor {size} {wrap} {tags} on:tagsupdate={saveTags} />
</NoteEditor>
