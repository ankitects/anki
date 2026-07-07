<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script context="module" lang="ts">
    import { registerPackage } from "@tslib/runtime-require";

    import lifecycleHooks from "$lib/sveltelib/lifecycle-hooks";

    import type { CodeMirrorAPI } from "../CodeMirror.svelte";
    import type { EditingInputAPI, FocusableInputAPI } from "../EditingArea.svelte";

    export interface PlainTextInputAPI extends EditingInputAPI {
        name: "plain-text";
        moveCaretToEnd(): void;
        toggle(): boolean;
        codeMirror: CodeMirrorAPI;
    }

    export function editingInputIsPlainText(
        editingInput: EditingInputAPI,
    ): editingInput is PlainTextInputAPI {
        return editingInput.name === "plain-text";
    }

    export const parsingInstructions: string[] = [];
    export const closeHTMLTags = writable(true);

    const [lifecycle, instances, setupLifecycleHooks] =
        lifecycleHooks<PlainTextInputAPI>();

    registerPackage("anki/PlainTextInput", {
        lifecycle,
        instances,
    });
</script>

<script lang="ts">
    import { singleCallback } from "@tslib/typing";
    import { onMount, tick } from "svelte";
    import { writable } from "svelte/store";

    import { pageTheme } from "$lib/sveltelib/theme";

    import { baseOptions, gutterOptions, htmlanki } from "../code-mirror";
    import CodeMirror from "../CodeMirror.svelte";
    import { context as editingAreaContext } from "../EditingArea.svelte";
    import { Flag } from "../helpers";
    import { context as noteEditorContext } from "../NoteEditor.svelte";
    import removeProhibitedTags from "./remove-prohibited";
    import { storedToUndecorated, undecoratedToStored } from "./transform";

    export let hidden = false;
    export let fieldCollapsed = false;
    export const focusFlag = new Flag();

    $: configuration = {
        mode: htmlanki,
        ...baseOptions,
        ...gutterOptions,
        ...{ autoCloseTags: $closeHTMLTags },
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
        pushUpdate(!(hidden || fieldCollapsed));
        tick().then(() => {
            refresh();
            if (focusFlag.checkAndReset()) {
                refocus();
            }
        });
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
    on:focusin={() => ($focusedInput = api)}
    {hidden}
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
        height: 100%;

        :global(.CodeMirror) {
            height: 100%;
            background: var(--canvas-code);
            padding-inline: 4px;
        }

        :global(.CodeMirror-lines) {
            padding: 8px 0;
        }

        :global(.CodeMirror-gutters) {
            background: var(--canvas-code);
        }
    }
</style>
