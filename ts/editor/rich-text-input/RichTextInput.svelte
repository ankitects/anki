<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script context="module" lang="ts">
    import type { ContentEditableAPI } from "../../editable/ContentEditable.svelte";
    import { singleCallback } from "../../lib/typing";
    import useContextProperty from "../../sveltelib/context-property";
    import useDOMMirror from "../../sveltelib/dom-mirror";
    import type { InputHandlerAPI } from "../../sveltelib/input-handler";
    import useInputHandler from "../../sveltelib/input-handler";
    import { pageTheme } from "../../sveltelib/theme";
    import type { EditingInputAPI, FocusableInputAPI } from "../EditingArea.svelte";
    /* import type CustomStyles from "./CustomStyles.svelte"; */

    export interface RichTextInputAPI extends EditingInputAPI {
        name: "rich-text";
        /** This is the contentEditable anki-editable element */
        element: Promise<HTMLElement>;
        moveCaretToEnd(): void;
        toggle(): boolean;
        preventResubscription(): () => void;
        inputHandler: InputHandlerAPI;
        /** The API exposed by the editable component */
        editable: ContentEditableAPI;
    }

    export function editingInputIsRichText(
        editingInput: EditingInputAPI | null,
    ): editingInput is RichTextInputAPI {
        return editingInput?.name === "rich-text";
    }

    const key = Symbol("richText");
    const [context, setContextProperty] =
        useContextProperty<RichTextInputAPI>(key);
    const [globalInputHandler, setupGlobalInputHandler] = useInputHandler();

    export { context, globalInputHandler as inputHandler };
</script>

<script lang="ts">
    import { getAllContexts, onMount } from "svelte";

    import { placeCaretAfterContent } from "../../domlib/place-caret";
    import ContentEditable from "../../editable/ContentEditable.svelte";
    import { context as editingAreaContext } from "../EditingArea.svelte";
    import { context as noteEditorContext } from "../NoteEditor.svelte";
    import getNormalizingNodeStore from "./normalizing-node-store";
    import useRichTextResolve from "./rich-text-resolve";
    import RichTextStyles from "./RichTextStyles.svelte";
    import SetContext from "./SetContext.svelte";
    import { fragmentToStored, storedToFragment } from "./transform";

    export let hidden: boolean;

    const { focusedInput } = noteEditorContext.get();
    const { content, editingInputs } = editingAreaContext.get();

    const nodes = getNormalizingNodeStore();
    const [richTextPromise, resolve] = useRichTextResolve();
    const { mirror, preventResubscription } = useDOMMirror();
    const [inputHandler, setupInputHandler] = useInputHandler();

    export function attachShadow(element: Element): void {
        element.attachShadow({ mode: "open" });
    }

    async function moveCaretToEnd(): Promise<void> {
        const richText = await richTextPromise;
        if (richText.textContent?.length === 0) {
            // Calling this method when richText is empty will cause the first keystroke of
            // ibus-based input methods with candidates to go double. For example, if you
            // type "a" it becomes "aa". This problem exists in many linux distributions.
            // When richText is empty, there is no need to place the caret, just return.
            return;
        }

        placeCaretAfterContent(richText);
    }

    async function focus(): Promise<void> {
        const richText = await richTextPromise;
        richText.focus();
    }

    async function refocus(): Promise<void> {
        const richText = await richTextPromise;
        richText.blur();
        richText.focus();
        moveCaretToEnd();
    }

    function toggle(): boolean {
        hidden = !hidden;
        return hidden;
    }

    const className = "rich-text-editable";
    let richTextDiv: HTMLElement;

    async function getInputAPI(target: EventTarget): Promise<FocusableInputAPI | null> {
        if (target === richTextDiv) {
            return api;
        }

        return null;
    }

    export const api: RichTextInputAPI = {
        name: "rich-text",
        element: richTextPromise,
        focus,
        refocus,
        focusable: !hidden,
        toggle,
        getInputAPI,
        moveCaretToEnd,
        preventResubscription,
        inputHandler,
        editable: {} as ContentEditableAPI,
    };

    const allContexts = getAllContexts();

    function attachContentEditable(element: Element, { stylesDidLoad }): void {
        (async () => {
            await stylesDidLoad;

            new ContentEditable({
                target: element.shadowRoot!,
                props: {
                    nodes,
                    resolve,
                    mirrors: [mirror],
                    inputHandlers: [setupInputHandler, setupGlobalInputHandler],
                    api: api.editable,
                },
                context: allContexts,
            });
        })();
    }

    function pushUpdate(isFocusable: boolean): void {
        api.focusable = isFocusable;
        $editingInputs = $editingInputs;
    }

    $: pushUpdate(!hidden);

    onMount(() => {
        $editingInputs.push(api);
        $editingInputs = $editingInputs;

        return singleCallback(
            content.subscribe((html: string): void =>
                nodes.setUnprocessed(storedToFragment(html)),
            ),
            nodes.subscribe((fragment: DocumentFragment): void =>
                content.set(fragmentToStored(fragment)),
            ),
        );
    });
</script>

<div class="rich-text-input" on:focusin={() => ($focusedInput = api)}>
    <RichTextStyles
        color={$pageTheme.isDark ? "white" : "black"}
        let:attachToShadow={attachStyles}
        let:promise={stylesPromise}
        let:stylesDidLoad
    >
        <div
            bind:this={richTextDiv}
            class={className}
            class:hidden
            class:night-mode={$pageTheme.isDark}
            use:attachShadow
            use:attachStyles
            use:attachContentEditable={{ stylesDidLoad }}
            on:focusin
            on:focusout
        />

        <div class="rich-text-widgets">
            {#await Promise.all( [richTextPromise, stylesPromise], ) then _}
                <SetContext
                    setter={setContextProperty}
                    value={api}
                >
                    <slot />
                </SetContext>
            {/await}
        </div>
    </RichTextStyles>
</div>

<style lang="scss">
    .hidden {
        display: none;
    }
</style>
