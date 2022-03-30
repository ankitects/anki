<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script context="module" lang="ts">
    /* import type { FocusHandlerAPI } from "../../editable/content-editable"; */
    import type { ContentEditableAPI } from "../../editable/ContentEditable.svelte";
    import useContextProperty from "../../sveltelib/context-property";
    import useDOMMirror from "../../sveltelib/dom-mirror";
    import type { InputHandlerAPI } from "../../sveltelib/input-handler";
    import useInputHandler from "../../sveltelib/input-handler";
    import { pageTheme } from "../../sveltelib/theme";
    import type { EditingInputAPI } from "../EditingArea.svelte";
    import type CustomStyles from "./CustomStyles.svelte";

    export interface RichTextInputAPI extends EditingInputAPI {
        name: "rich-text";
        element: Promise<HTMLElement>;
        moveCaretToEnd(): void;
        toggle(): boolean;
        preventResubscription(): () => void;
        inputHandler: InputHandlerAPI;
        editable: ContentEditableAPI;
    }

    export function editingInputIsRichText(
        editingInput: EditingInputAPI | null,
    ): editingInput is RichTextInputAPI {
        return editingInput?.name === "rich-text";
    }

    export interface RichTextInputContextAPI {
        styles: CustomStyles;
        container: HTMLElement;
        api: RichTextInputAPI;
    }

    const key = Symbol("richText");
    const [context, setContextProperty] =
        useContextProperty<RichTextInputContextAPI>(key);
    const [globalInputHandler, setupGlobalInputHandler] = useInputHandler();

    export { context, globalInputHandler as inputHandler };
</script>

<script lang="ts">
    import { getAllContexts, onMount } from "svelte";

    import { placeCaretAfterContent } from "../../domlib/place-caret";
    import ContentEditable from "../../editable/ContentEditable.svelte";
    import useRichTextResolve from "./rich-text-resolve";
    import getNormalizingNodeStore from "./normalizing-node-store";
    import { fragmentToStored, storedToFragment } from "./transform";
    import { context as editingAreaContext } from "../EditingArea.svelte";
    import { context as noteEditorContext } from "../NoteEditor.svelte";
    import RichTextStyles from "./RichTextStyles.svelte";
    import SetContext from "./SetContext.svelte";

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

    export const api: RichTextInputAPI = {
        name: "rich-text",
        element: richTextPromise,
        focus,
        refocus,
        focusable: !hidden,
        toggle,
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

        const unsubscribeFromEditingArea = content.subscribe((html: string): void =>
            nodes.setUnprocessed(storedToFragment(html)),
        );
        const unsubscribeToEditingArea = nodes.subscribe(
            (fragment: DocumentFragment): void =>
                content.set(fragmentToStored(fragment)),
        );

        return () => {
            unsubscribeFromEditingArea();
            unsubscribeToEditingArea();
        };
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
            class="rich-text-editable"
            class:hidden
            class:night-mode={$pageTheme.isDark}
            use:attachShadow
            use:attachStyles
            use:attachContentEditable={{ stylesDidLoad }}
            on:focusin
            on:focusout
        />

        <div class="rich-text-widgets">
            {#await Promise.all( [richTextPromise, stylesPromise], ) then [container, styles]}
                <SetContext
                    setter={setContextProperty}
                    value={{ container, styles, api }}
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
