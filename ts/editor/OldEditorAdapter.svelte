<script lang="ts">
    import NoteEditor from "./NoteEditor.svelte";
    import { bridgeCommand } from "lib/bridgecommand";
    import { isApplePlatform } from "lib/platform";
    import { ChangeTimer } from "./change-timer";

    let noteEditor: NoteEditor;

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

    $: data = {
        fieldsData: fields.map(([fieldName, fieldContent], index) => ({
            fieldName,
            fieldContent,
            fontName: fonts[index][0],
            fontSize: fonts[index][1],
            rtl: fonts[index][2],
            sticky: stickies ? stickies[index] : null,
        })),
        tags,
        textColor,
        highlightColor,
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
            /* () => bridgeCommand(`key:${index}:${noteId}:${noteEditor.fields[index].editingArea.activeInput.fieldHTML}`), */
            () => console.log("save!"),
            600
        );
    }

    function onFieldBlur({ detail }): void {
        /* this will also be a key save */
        fieldSave.fireImmediately();
    }
</script>

<NoteEditor
    bind:this={noteEditor}
    {data}
    size={isApplePlatform() ? 1.6 : 2.0}
    wrap
    {...$$restProps}
    on:tagsupdate={saveTags}
    on:fieldupdate={onFieldUpdate}
    on:fieldblur={onFieldBlur}
/>
