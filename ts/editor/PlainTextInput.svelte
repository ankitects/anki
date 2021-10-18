<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script context="module" lang="ts">
    import type { EditingInputAPI } from "./EditingArea.svelte";

    export interface PlainTextInputAPI extends EditingInputAPI {
        name: "plain-text";
        moveCaretToEnd(): void;
        toggle(): boolean;
        surround(before: string, after: string): void;
    }
</script>

<script lang="ts">
    import CodeMirror from "./CodeMirror.svelte";
    import type { CodeMirrorAPI } from "./CodeMirror.svelte";

    import { tick, onMount } from "svelte";
    import { writable } from "svelte/store";
    import { getDecoratedElements } from "./DecoratedElements.svelte";
    import { getEditingArea } from "./EditingArea.svelte";
    import { htmlanki, baseOptions, gutterOptions } from "./code-mirror";

    export let hidden = false;

    const configuration = {
        mode: htmlanki,
        ...baseOptions,
        ...gutterOptions,
    };

    const { editingInputs, content } = getEditingArea();
    const decoratedElements = getDecoratedElements();
    const code = writable($content);

    function adjustInputHTML(html: string): string {
        for (const component of decoratedElements) {
            html = component.toUndecorated(html);
        }

        return html;
    }

    const parser = new DOMParser();
    // TODO Expose this somehow
    const parseStyle = "<style>anki-mathjax { white-space: pre; }</style>";

    function parseAsHTML(html: string): string {
        const doc = parser.parseFromString(parseStyle + html, "text/html");
        const body = doc.body;

        for (const script of body.getElementsByTagName("script")) {
            script.remove();
        }

        for (const script of body.getElementsByTagName("link")) {
            script.remove();
        }

        for (const style of body.getElementsByTagName("style")) {
            style.remove();
        }

        return doc.body.innerHTML;
    }

    function adjustOutputHTML(html: string): string {
        for (const component of decoratedElements) {
            html = component.toStored(html);
        }

        return html;
    }

    let codeMirror: CodeMirrorAPI;

    function focus(): void {
        codeMirror?.editor.focus();
    }

    function refocus(): void {
        (codeMirror?.editor as any).display.input.blur();
        focus();
    }

    function moveCaretToEnd(): void {
        codeMirror?.editor.setCursor(codeMirror.editor.lineCount(), 0);
    }

    function surround(before: string, after: string): void {
        const selection = codeMirror?.editor.getSelection();
        codeMirror?.editor.replaceSelection(before + selection + after);
    }

    export const api = {
        name: "plain-text",
        focus,
        focusable: !hidden,
        moveCaretToEnd,
        refocus,
        toggle(): boolean {
            hidden = !hidden;
            return hidden;
        },
        surround,
    } as PlainTextInputAPI;

    function pushUpdate(): void {
        api.focusable = !hidden;
        $editingInputs = $editingInputs;
    }

    function refresh() {
        codeMirror.editor.refresh();
    }

    $: {
        hidden;
        tick().then(refresh);
        pushUpdate();
    }

    onMount(() => {
        $editingInputs.push(api);
        $editingInputs = $editingInputs;

        const unsubscribeFromEditingArea = content.subscribe((value: string): void => {
            const adjusted = adjustInputHTML(value);
            code.set(adjusted);
        });

        const unsubscribeToEditingArea = code.subscribe((value: string): void => {
            const parsed = parseAsHTML(value);
            content.set(adjustOutputHTML(parsed));
        });

        return () => {
            unsubscribeFromEditingArea();
            unsubscribeToEditingArea();
        };
    });
</script>

<div class:hidden on:focusin on:focusout>
    <CodeMirror
        {configuration}
        {code}
        bind:api={codeMirror}
        on:focus={moveCaretToEnd}
        on:change={({ detail: html }) => code.set(parseAsHTML(html))}
    />
</div>

<style lang="scss">
    .hidden {
        display: none;
    }
</style>
