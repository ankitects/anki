<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script context="module" lang="ts">
    import { registerPackage } from "../../lib/runtime-require";
    import lifecycleHooks from "../../sveltelib/lifecycle-hooks";
    import type { CodeMirrorAPI } from "../CodeMirror.svelte";
    import type { EditingInputAPI, FocusableInputAPI } from "../EditingArea.svelte";

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

    import { singleCallback } from "../../lib/typing";
    import { pageTheme } from "../../sveltelib/theme";
    import { baseOptions, gutterOptions, htmlanki } from "../code-mirror";
    import CodeMirror from "../CodeMirror.svelte";
    import { context as editingAreaContext } from "../EditingArea.svelte";
    import { context as noteEditorContext } from "../NoteEditor.svelte";
    import removeProhibitedTags from "./remove-prohibited";
    import { storedToUndecorated, undecoratedToStored } from "./transform";

    export let hidden: boolean;

    const configuration = {
        mode: htmlanki,
        ...baseOptions,
        ...gutterOptions,
    };

    const { focusedInput } = noteEditorContext.get();
    const { editingInputs, content } = editingAreaContext.get();
    const code = writable($content);

    let codeMirror = {} as CodeMirrorAPI;

    async function focus(): Promise<void> {
        const editor = await codeMirror.editor;
        editor.focus();
    }

    async function moveCaretToEnd(): Promise<void> {
        const editor = await codeMirror.editor;
        editor.setCursor(editor.lineCount(), 0);
    }

    async function refocus(): Promise<void> {
        const editor = (await codeMirror.editor) as any;
        editor.display.input.blur();

        focus();
        moveCaretToEnd();
    }

    function toggle(): boolean {
        hidden = !hidden;
        return hidden;
    }

    async function getInputAPI(target: EventTarget): Promise<FocusableInputAPI | null> {
        const editor = (await codeMirror.editor) as any;

        if (target === editor.display.input.textarea) {
            return api;
        }

        return null;
    }

    export const api: PlainTextInputAPI = {
        name: "plain-text",
        focus,
        focusable: !hidden,
        moveCaretToEnd,
        refocus,
        toggle,
        getInputAPI,
        codeMirror,
    };

    /**
     * Communicate to editing area that input is not focusable
     */
    function pushUpdate(isFocusable: boolean): void {
        api.focusable = isFocusable;
        $editingInputs = $editingInputs;
    }

    async function refresh(): Promise<void> {
        const editor = await codeMirror.editor;
        editor.refresh();
    }

    $: {
        pushUpdate(!hidden);
        tick().then(refresh);
    }

    function onChange({ detail: html }: CustomEvent<string>): void {
        code.set(removeProhibitedTags(html));
    }

    onMount(() => {
        $editingInputs.push(api);
        $editingInputs = $editingInputs;

        return singleCallback(
            content.subscribe((html: string): void =>
                /* We call `removeProhibitedTags` here, because content might
                 * have been changed outside the editor, and we need to parse
                 * it to get the "neutral" value. Otherwise, there might be
                 * conflicts with other editing inputs */
                code.set(removeProhibitedTags(storedToUndecorated(html))),
            ),
            code.subscribe((html: string): void =>
                content.set(undecoratedToStored(html)),
            ),
        );
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
        {hidden}
        bind:api={codeMirror}
        on:change={onChange}
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
