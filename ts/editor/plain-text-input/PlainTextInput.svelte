<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script context="module" lang="ts">
    import { registerPackage } from "../../lib/runtime-require";
    import lifecycleHooks from "../../sveltelib/lifecycle-hooks";
    import type { CodeMirrorAPI } from "../CodeMirror.svelte";
    import type { EditingInputAPI } from "../EditingArea.svelte";

    export interface PlainTextInputAPI extends EditingInputAPI {
        name: "plain-text";
        moveCaretToEnd(): void;
        toggle(): boolean;
        codeMirror: CodeMirrorAPI;
    }

    export const parsingInstructions: string[] = [];

    const [lifecycle, instances, setupLifecycleHooks] =
        lifecycleHooks<PlainTextInputAPI>();

    registerPackage("anki/PlainTextInput", {
        lifecycle,
        instances,
    });
</script>

<script lang="ts">
    import { onMount, tick } from "svelte";
    import { writable } from "svelte/store";

    import { pageTheme } from "../../sveltelib/theme";
    import { baseOptions, gutterOptions, htmlanki } from "../code-mirror";
    import CodeMirror from "../CodeMirror.svelte";
    import { context as decoratedElementsContext } from "../DecoratedElements.svelte";
    import { context as editingAreaContext } from "../EditingArea.svelte";
    import { context as noteEditorContext } from "../NoteEditor.svelte";
    import removeProhibitedTags from "./remove-prohibited";

    export let hidden = false;

    const configuration = {
        mode: htmlanki,
        ...baseOptions,
        ...gutterOptions,
    };

    const { focusedInput } = noteEditorContext.get();

    const { editingInputs, content } = editingAreaContext.get();
    const decoratedElements = decoratedElementsContext.get();
    const code = writable($content);

    function focus(): void {
        codeMirror.editor.then((editor) => editor.focus());
    }

    function moveCaretToEnd(): void {
        codeMirror.editor.then((editor) => editor.setCursor(editor.lineCount(), 0));
    }

    function refocus(): void {
        codeMirror.editor.then((editor) => (editor as any).display.input.blur());
        focus();
        moveCaretToEnd();
    }

    function toggle(): boolean {
        hidden = !hidden;
        return hidden;
    }

    let codeMirror = {} as CodeMirrorAPI;

    export const api = {
        name: "plain-text",
        focus,
        focusable: !hidden,
        moveCaretToEnd,
        refocus,
        toggle,
        codeMirror,
    } as PlainTextInputAPI;

    function pushUpdate(): void {
        api.focusable = !hidden;
        $editingInputs = $editingInputs;
    }

    function refresh(): void {
        codeMirror.editor.then((editor) => editor.refresh());
    }

    $: {
        hidden;
        tick().then(refresh);
        pushUpdate();
    }

    function storedToUndecorated(html: string): string {
        return decoratedElements.toUndecorated(html);
    }

    function undecoratedToStored(html: string): string {
        return decoratedElements.toStored(html);
    }

    onMount(() => {
        $editingInputs.push(api);
        $editingInputs = $editingInputs;

        const unsubscribeFromEditingArea = content.subscribe((value: string): void => {
            code.set(storedToUndecorated(value));
        });

        const unsubscribeToEditingArea = code.subscribe((value: string): void => {
            content.set(removeProhibitedTags(undecoratedToStored(value)));
        });

        return () => {
            unsubscribeFromEditingArea();
            unsubscribeToEditingArea();
        };
    });

    setupLifecycleHooks(api);
</script>

<div
    class="plain-text-input"
    class:light-theme={!$pageTheme.isDark}
    class:hidden
    on:focusin={() => ($focusedInput = api)}
>
    <CodeMirror
        {configuration}
        {code}
        bind:api={codeMirror}
        on:change={({ detail: html }) => code.set(removeProhibitedTags(html))}
    />
</div>

<style lang="scss">
    .plain-text-input {
        overflow-x: hidden;

        :global(.CodeMirror) {
            border-radius: 0 0 5px 5px;
        }

        :global(.CodeMirror-lines) {
            padding: 6px 0;
        }

        &.hidden {
            display: none;
        }
    }

    .light-theme :global(.CodeMirror) {
        border-top: 1px solid #ddd;
    }
</style>
