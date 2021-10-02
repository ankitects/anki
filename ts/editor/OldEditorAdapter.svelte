<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import NoteEditor from "./NoteEditor.svelte";

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
    import { writable } from "svelte/store";
    import { bridgeCommand } from "../lib/bridgecommand";
    import { isApplePlatform } from "../lib/platform";
    import { setContext, focusInEditableKey, activeInputKey } from "./context";
    import { ChangeTimer } from "./change-timer";
    import { alertIcon } from "./icons";

    function quoteFontFamily(fontFamily: string): string {
        // generic families (e.g. sans-serif) must not be quoted
        if (!/^[-a-z]+$/.test(fontFamily)) {
            fontFamily = `"${fontFamily}"`;
        }
        return fontFamily;
    }

    let noteEditor: NoteEditor;
    let size = isApplePlatform() ? 1.6 : 2.0;
    let wrap = true;

    let fields: [string, string][] = [];
    export function setFields(fs: [string, string][]): void {
        fields = fs;
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

    let tags: string[] = [];
    export function setTags(ts: string[]): void {
        tags = ts;
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

    $: data = {
        fieldsData: fields.map(([fieldName, fieldContent], index) => ({
            fieldName,
            fieldContent,
            fontName: quoteFontFamily(fonts[index][0]),
            fontSize: fonts[index][1],
            rtl: fonts[index][2],
            sticky: stickies ? stickies[index] : null,
            dupe: cols[index] === "dupe",
        })),
        tags,
        focusTo,
    };

    function saveTags(event: CustomEvent): void {
        bridgeCommand(`saveTags:${JSON.stringify(event.detail.tags)}`);
    }

    const fieldSave = new ChangeTimer();

    function onFieldUpdate({ detail }): void {
        const { index, content } = detail;

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

    onMount(() => {
        document.addEventListener("visibilitychange", saveOnPageHide);
        return () => document.removeEventListener("visibilitychange", saveOnPageHide);
    });

    onDestroy(() => deregisterSticky);
</script>

<NoteEditor
    bind:this={noteEditor}
    {data}
    {size}
    {wrap}
    {...$$restProps}
    on:tagsupdate={saveTags}
    on:fieldupdate={onFieldUpdate}
>
    <svelte:fragment slot="widgets">
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
    </svelte:fragment>

    <svelte:fragment slot="field-state" let:index>
        <EditableBadge bind:off={editablesHidden[index]} />
        <CodableBadge bind:off={codablesHidden[index]} />
        {#if stickies}
            <StickyBadge active={stickies[index]} {index} />
        {/if}
    </svelte:fragment>

    <svelte:fragment slot="editing-inputs" let:index>
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
</NoteEditor>
