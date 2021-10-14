<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import NoteEditor from "./NoteEditor.svelte";
    import MultiRootEditor from "./MultiRootEditor.svelte";
    import Fields from "./Fields.svelte";
    import EditorField from "./EditorField.svelte";
    import TagEditor from "./TagEditor.svelte";

    import EditorToolbar from "./EditorToolbar.svelte";
    import Notification from "./Notification.svelte";
    import Absolute from "../components/Absolute.svelte";
    import Badge from "../components/Badge.svelte";

    import DecoratedElements from "./DecoratedElements.svelte";
    import Editable from "./Editable.svelte";
    import { MathjaxHandle } from "./mathjax-overlay";
    import { ImageHandle } from "./image-overlay";
    import Codable from "./Codable.svelte";

    import EditableBadge from "./EditableBadge.svelte";
    import CodableBadge from "./CodableBadge.svelte";
    import StickyBadge from "./StickyBadge.svelte";

    import { onMount, onDestroy } from "svelte";
    import type { Writable } from "svelte/store";
    import { writable } from "svelte/store";
    import { bridgeCommand } from "../lib/bridgecommand";
    import { isApplePlatform } from "../lib/platform";
    import {
        setContext,
        noteEditorKey,
        focusInEditableKey,
        activeInputKey,
    } from "./context";
    import { ChangeTimer } from "./change-timer";
    import { alertIcon } from "./icons";

    function quoteFontFamily(fontFamily: string): string {
        // generic families (e.g. sans-serif) must not be quoted
        if (!/^[-a-z]+$/.test(fontFamily)) {
            fontFamily = `"${fontFamily}"`;
        }
        return fontFamily;
    }

    let size = isApplePlatform() ? 1.6 : 2.0;
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

        for (let i = fieldStores.length; i > newFieldNames.length; i++) {
            fieldStores.pop();
        }

        for (const [index, [_, fieldContent]] of fs.entries()) {
            fieldStores[index].set(fieldContent);
        }

        fieldNames = newFieldNames;
    }

    let fonts: [string, number, boolean][] = [];
    let editablesHidden: boolean[] = [];
    let codablesHidden: boolean[] = [];

    export function setFonts(fs: [string, number, boolean][]): void {
        fonts = fs;

        editablesHidden = fonts.map(() => false);
        codablesHidden = fonts.map(() => true);
    }

    let focusTo: number = 0;
    export function focusField(n: number): void {
        focusTo = n;
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

    $: fieldsData = fieldNames.map((fieldName, index) => ({
        fieldName,
        fontName: quoteFontFamily(fonts[index][0]),
        fontSize: fonts[index][1],
        rtl: fonts[index][2],
        sticky: stickies ? stickies[index] : null,
        dupe: cols[index] === "dupe",
    }));

    function saveTags({ detail }: CustomEvent): void {
        bridgeCommand(`saveTags:${JSON.stringify(detail.tags)}`);
    }

    const fieldSave = new ChangeTimer();

    function updateField(index: number, content: string): void {
        fieldSave.schedule(
            () => bridgeCommand(`key:${index}:${getNoteId()}:${content}`),
            600
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

    import { registerShortcut } from "../lib/shortcuts";

    let deregisterSticky: () => void;
    export function activateStickyShortcuts() {
        deregisterSticky = registerShortcut(toggleStickyAll, "Shift+F9");
    }

    export function focusIfField(x: number, y: number): boolean {
        const elements = document.elementsFromPoint(x, y);
        const first = elements[0];

        if (first.shadowRoot) {
            const editable = first.shadowRoot.lastElementChild! as HTMLElement;
            editable.focus();
            return true;
        }

        return false;
    }

    const focusInEditable = writable(false);
    setContext(focusInEditableKey, focusInEditable);

    let editables: Editable[] = [];
    $: editables = editables.filter(Boolean);

    let codables: Codable[] = [];
    $: codables = codables.filter(Boolean);

    const activeInput = setContext(activeInputKey, writable(null));

    const currentField = writable(null);
    let editorFields: EditorField[] = [];

    $: fieldApis = editorFields.filter(Boolean).map((field) => field.api);

    export const api = setContext(
        noteEditorKey,
        Object.create(
            {
                currentField,
            },
            {
                fields: { get: () => fieldApis },
            }
        )
    );

    onMount(() => {
        document.addEventListener("visibilitychange", saveOnPageHide);
        return () => document.removeEventListener("visibilitychange", saveOnPageHide);
    });

    onDestroy(() => deregisterSticky);
</script>

<NoteEditor>
    <MultiRootEditor>
        <EditorToolbar {size} {wrap} {textColor} {highlightColor} />

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
            {#each fieldsData as field, index}
                <EditorField
                    {field}
                    content={fieldStores[index]}
                    autofocus={index === focusTo}
                    bind:this={editorFields[index]}
                    on:focusin={() => {
                        $currentField = editorFields[index].api;
                    }}
                    on:focusout={() => {
                        $currentField = null;
                    }}
                >
                    <svelte:fragment slot="field-state">
                        <EditableBadge bind:off={editablesHidden[index]} />
                        <CodableBadge bind:off={codablesHidden[index]} />
                        {#if stickies}
                            <StickyBadge active={stickies[index]} {index} />
                        {/if}
                    </svelte:fragment>

                    <svelte:fragment slot="editing-inputs">
                        <DecoratedElements>
                            <Editable
                                hidden={editablesHidden[index]}
                                on:focusin={() => {
                                    $focusInEditable = true;
                                    $activeInput = editables[index].api;
                                }}
                                on:focusout={() => {
                                    $focusInEditable = false;
                                    $activeInput = null;
                                    saveFieldNow();
                                }}
                                bind:this={editables[index]}
                            >
                                <ImageHandle />
                                <MathjaxHandle />
                            </Editable>

                            <Codable
                                hidden={codablesHidden[index]}
                                on:focusin={() => {
                                    $activeInput = codables[index].api;
                                }}
                                on:focusout={() => {
                                    $activeInput = null;
                                    saveFieldNow();
                                }}
                                bind:this={codables[index]}
                            />
                        </DecoratedElements>
                    </svelte:fragment>
                </EditorField>
            {/each}
        </Fields>
    </MultiRootEditor>

    <TagEditor {size} {wrap} {tags} on:tagsupdate={saveTags} />
</NoteEditor>
