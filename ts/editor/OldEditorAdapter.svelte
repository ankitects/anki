<script lang="ts">
    import NoteEditor from "./NoteEditor.svelte";
    import EditorToolbar from "./EditorToolbar.svelte";
    import EditableAdapter from "./EditableAdapter.svelte";
    import CodableAdapter from "./CodableAdapter.svelte";
    import { bridgeCommand } from "lib/bridgecommand";
    import { isApplePlatform } from "lib/platform";
    import { ChangeTimer } from "./change-timer";
    import { getNoteId } from "./note-id";

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
    export function setFonts(fs: [string, number, boolean][]): void {
        fonts = fs;
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

    $: data = {
        fieldsData: fields.map(([fieldName, fieldContent], index) => ({
            fieldName,
            fieldContent,
            fontName: quoteFontFamily(fonts[index][0]),
            fontSize: fonts[index][1],
            rtl: fonts[index][2],
            sticky: stickies ? stickies[index] : null,
        })),
        tags,
        focusTo,
    };

    let cols: ("dupe" | "")[];
    export function setBackgrounds(cls: ("dupe" | "")[]): void {
        cols = cls;
    }

    let hint: string;
    export function setClozeHint(hnt: string): void {
        hint = hnt;
    }

    function saveTags(event: CustomEvent): void {
        bridgeCommand(`saveTags:${JSON.stringify(event.detail.tags)}`);
    }

    const fieldSave = new ChangeTimer();

    function onFieldUpdate({ detail: index }): void {
        fieldSave.schedule(
            () =>
                bridgeCommand(
                    `key:${index}:${getNoteId()}:${
                        (noteEditor.api.multiRootEditor!.fields[index] as any).editingArea
                            .fieldHTML
                    }`
                ),
            600
        );
    }

    function onFieldBlur(): void {
        /* this will also be a key save */
        fieldSave.fireImmediately();
    }
</script>

<NoteEditor
    bind:this={noteEditor}
    editingInputs={[EditableAdapter, CodableAdapter]}
    {data}
    {size}
    {wrap}
    {...$$restProps}
    on:tagsupdate={saveTags}
    on:fieldupdate={onFieldUpdate}
    on:fieldblur={onFieldBlur}
>
    <EditorToolbar slot="toolbar" {size} {wrap} {textColor} {highlightColor} />
</NoteEditor>
