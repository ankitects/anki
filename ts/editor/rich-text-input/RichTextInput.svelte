<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script context="module" lang="ts">
    import { writable } from "svelte/store";

    import type { InputHandlerAPI } from "$lib/sveltelib/input-handler";

    import type { ContentEditableAPI } from "../../editable/ContentEditable.svelte";
    import type { EditingInputAPI, FocusableInputAPI } from "../EditingArea.svelte";
    import type { SurroundedAPI } from "../surround";

    export interface RichTextInputAPI extends EditingInputAPI, SurroundedAPI {
        name: "rich-text";
        /** This is the contentEditable anki-editable element */
        element: Promise<HTMLElement>;
        moveCaretToEnd(): void;
        toggle(): boolean;
        preventResubscription(): () => void;
        inputHandler: InputHandlerAPI;
        /** The API exposed by the editable component */
        editable: ContentEditableAPI;
        customStyles: Promise<Record<string, any>>;
        isClozeField: boolean;
    }

    function editingInputIsRichText(
        editingInput: EditingInputAPI,
    ): editingInput is RichTextInputAPI {
        return editingInput.name === "rich-text";
    }

    import { registerPackage } from "@tslib/runtime-require";

    import contextProperty from "$lib/sveltelib/context-property";
    import lifecycleHooks from "$lib/sveltelib/lifecycle-hooks";

    import { Surrounder } from "../surround";

    const key = Symbol("richText");
    const [context, setContextProperty] = contextProperty<RichTextInputAPI>(key);
    const [globalInputHandler, setupGlobalInputHandler] = useInputHandler();
    const [lifecycle, instances, setupLifecycleHooks] =
        lifecycleHooks<RichTextInputAPI>();
    const apiStore = writable<SurroundedAPI | null>(null);
    const surrounder = Surrounder.make<string>(apiStore);

    registerPackage("anki/RichTextInput", {
        context,
        surrounder,
        lifecycle,
        instances,
    });

    export {
        context,
        editingInputIsRichText,
        globalInputHandler as inputHandler,
        lifecycle,
        surrounder,
    };
</script>

<script lang="ts">
    import { directionKey, fontFamilyKey, fontSizeKey } from "@tslib/context-keys";
    import { promiseWithResolver } from "@tslib/promise";
    import { singleCallback } from "@tslib/typing";
    import { getAllContexts, getContext, mount, onMount, tick } from "svelte";
    import type { Readable } from "svelte/store";

    import { placeCaretAfterContent } from "$lib/domlib/place-caret";
    import useDOMMirror from "$lib/sveltelib/dom-mirror";
    import useInputHandler from "$lib/sveltelib/input-handler";
    import { pageTheme } from "$lib/sveltelib/theme";

    import ContentEditable from "../../editable/ContentEditable.svelte";
    import { context as editingAreaContext } from "../EditingArea.svelte";
    import { Flag } from "../helpers";
    import { context as noteEditorContext } from "../NoteEditor.svelte";
    import getNormalizingNodeStore from "./normalizing-node-store";
    import useRichTextResolve from "./rich-text-resolve";
    import RichTextStyles from "./RichTextStyles.svelte";
    import { fragmentToStored, storedToFragment } from "./transform";

    export let hidden = false;
    export const focusFlag = new Flag();
    export let isClozeField: boolean;

    const { focusedInput } = noteEditorContext.get();
    const { content, editingInputs } = editingAreaContext.get();

    const fontFamily = getContext<Readable<string>>(fontFamilyKey);
    const fontSize = getContext<Readable<number>>(fontSizeKey);
    const direction = getContext<Readable<"ltr" | "rtl">>(directionKey);

    const nodes = getNormalizingNodeStore();
    const [richTextPromise, resolve] = useRichTextResolve();
    const { mirror, preventResubscription } = useDOMMirror();
    const [inputHandler, setupInputHandler] = useInputHandler();
    const [customStyles, stylesResolve] = promiseWithResolver<Record<string, any>>();

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
        richText.blur();
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
        customStyles,
        isClozeField,
    };

    const allContexts = getAllContexts();

    function attachContentEditable(element: Element, { stylesDidLoad }): void {
        (async () => {
            await stylesDidLoad;

            mount(ContentEditable, {
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

    function setFocus(): void {
        $focusedInput = api;
        $apiStore = api;
    }

    function removeFocus(): void {
        // We do not unset focusedInput here.
        // If we did, UI components for the input would react the store
        // being unset, even though most likely it will be set to some other
        // field right away.

        $apiStore = null;
    }

    $: {
        pushUpdate(!hidden);
        if (focusFlag.checkAndReset()) {
            tick().then(refocus);
        }
    }

    $: {
        api.isClozeField = isClozeField;
    }

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

    setContextProperty(api);
    setupLifecycleHooks(api);
</script>

<div class="rich-text-input" on:focusin={setFocus} on:focusout={removeFocus} {hidden}>
    <RichTextStyles
        color={$pageTheme.isDark ? "white" : "black"}
        fontFamily={$fontFamily}
        fontSize={$fontSize}
        direction={$direction}
        callback={stylesResolve}
        let:attachToShadow={attachStyles}
        let:stylesDidLoad
    >
        <div class="rich-text-relative">
            <div
                class="rich-text-editable"
                class:empty={$content.length === 0}
                bind:this={richTextDiv}
                use:attachShadow
                use:attachStyles
                use:attachContentEditable={{ stylesDidLoad }}
                on:focusin
                on:focusout
            ></div>

            {#await Promise.all([richTextPromise, stylesDidLoad]) then _}
                <div class="rich-text-widgets">
                    <slot />
                </div>
            {/await}
        </div>
    </RichTextStyles>
</div>

<style lang="scss">
    .rich-text-input {
        height: 100%;

        background-color: var(--canvas-elevated);
        padding: 6px;
    }

    .rich-text-relative {
        position: relative;
    }

    .rich-text-editable.empty::before {
        position: absolute;
        color: var(--fg-subtle);
        content: var(--description-content);
        font-size: var(--description-font-size, 20px);
        cursor: text;
        max-width: 95%;
        overflow-x: hidden;
        white-space: nowrap;
        text-overflow: ellipsis;
    }
</style>
