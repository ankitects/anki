<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script context="module" lang="ts">
    import type { FocusHandlerAPI } from "../../editable/content-editable";
    import type { ContentEditableAPI } from "../../editable/ContentEditable.svelte";
    import useContextProperty from "../../sveltelib/context-property";
    import useDOMMirror from "../../sveltelib/dom-mirror";
    import type { InputHandlerAPI } from "../../sveltelib/input-handler";
    import useInputHandler from "../../sveltelib/input-handler";
    import { pageTheme } from "../../sveltelib/theme";
    import type { EditingInputAPI } from "../EditingArea.svelte";
    import type CustomStyles from "./CustomStyles.svelte";

    export interface RichTextInputAPI extends EditingInputAPI, ContentEditableAPI {
        name: "rich-text";
        shadowRoot: Promise<ShadowRoot>;
        element: Promise<HTMLElement>;
        moveCaretToEnd(): void;
        toggle(): boolean;
        preventResubscription(): () => void;
        inputHandler: InputHandlerAPI;
        focusHandler: FocusHandlerAPI;
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
    import type { DecoratedElement } from "../../editable/decorated";
    import { bridgeCommand } from "../../lib/bridgecommand";
    import {
        fragmentToString,
        nodeContainsInlineContent,
        nodeIsElement,
    } from "../../lib/dom";
    import { on } from "../../lib/events";
    import { promiseWithResolver } from "../../lib/promise";
    import { nodeStore } from "../../sveltelib/node-store";
    import { context as decoratedElementsContext } from "../DecoratedElements.svelte";
    import { context as editingAreaContext } from "../EditingArea.svelte";
    import { context as noteEditorContext } from "../NoteEditor.svelte";
    import RichTextStyles from "./RichTextStyles.svelte";
    import SetContext from "./SetContext.svelte";

    export let hidden: boolean;

    const { focusedInput } = noteEditorContext.get();

    const { content, editingInputs } = editingAreaContext.get();
    const decoratedElements = decoratedElementsContext.get();

    function normalizeFragment(fragment: DocumentFragment): void {
        fragment.normalize();

        for (const decorated of decoratedElements) {
            for (const element of fragment.querySelectorAll(
                decorated.tagName,
            ) as NodeListOf<DecoratedElement>) {
                element.undecorate();
            }
        }
    }

    const nodes = nodeStore<DocumentFragment>(undefined, normalizeFragment);

    function adjustInputHTML(html: string): string {
        for (const component of decoratedElements) {
            html = component.toUndecorated(html);
        }

        return html;
    }

    function adjustInputFragment(fragment: DocumentFragment): void {
        if (nodeContainsInlineContent(fragment)) {
            fragment.appendChild(document.createElement("br"));
        }
    }

    function writeFromEditingArea(html: string): void {
        /* We need .createContextualFragment so that customElements are initialized */
        const fragment = document
            .createRange()
            .createContextualFragment(adjustInputHTML(html));
        adjustInputFragment(fragment);
        nodes.setUnprocessed(fragment);
    }

    function adjustOutputFragment(fragment: DocumentFragment): void {
        if (
            fragment.hasChildNodes() &&
            nodeIsElement(fragment.lastChild!) &&
            nodeContainsInlineContent(fragment) &&
            fragment.lastChild!.tagName === "BR"
        ) {
            fragment.lastChild!.remove();
        }
    }

    function adjustOutputHTML(html: string): string {
        for (const component of decoratedElements) {
            html = component.toStored(html);
        }

        return html;
    }

    function writeToEditingArea(fragment: DocumentFragment): void {
        const clone = document.importNode(fragment, true);
        adjustOutputFragment(clone);

        const output = adjustOutputHTML(fragmentToString(clone));
        content.set(output);
    }

    const [shadowPromise, shadowResolve] = promiseWithResolver<ShadowRoot>();

    function attachShadow(element: Element): void {
        shadowResolve(element.attachShadow({ mode: "open" }));
    }

    const [richTextPromise, richTextResolve] = promiseWithResolver<HTMLElement>();

    function resolve(richTextInput: HTMLElement): { destroy: () => void } {
        function onPaste(event: Event): void {
            event.preventDefault();
            bridgeCommand("paste");
        }

        function onCutOrCopy(): void {
            bridgeCommand("cutOrCopy");
        }

        const removePaste = on(richTextInput, "paste", onPaste);
        const removeCopy = on(richTextInput, "copy", onCutOrCopy);
        const removeCut = on(richTextInput, "cut", onCutOrCopy);
        richTextResolve(richTextInput);

        return {
            destroy() {
                removePaste();
                removeCopy();
                removeCut();
            },
        };
    }

    const { mirror, preventResubscription } = useDOMMirror();
    const [inputHandler, setupInputHandler] = useInputHandler();

    function moveCaretToEnd() {
        richTextPromise.then(placeCaretAfterContent);
    }

    export const api = {
        name: "rich-text",
        shadowRoot: shadowPromise,
        element: richTextPromise,
        focus() {
            richTextPromise.then((richText) => {
                richText.focus();
            });
        },
        refocus() {
            richTextPromise.then((richText) => {
                richText.blur();
                richText.focus();
                moveCaretToEnd();
            });
        },
        focusable: !hidden,
        toggle(): boolean {
            hidden = !hidden;
            return hidden;
        },
        moveCaretToEnd,
        preventResubscription,
        inputHandler,
    } as RichTextInputAPI;

    const allContexts = getAllContexts();

    function attachContentEditable(element: Element, { stylesDidLoad }): void {
        stylesDidLoad.then(
            () =>
                new ContentEditable({
                    target: element.shadowRoot,
                    props: {
                        nodes,
                        resolve,
                        mirrors: [mirror],
                        inputHandlers: [setupInputHandler, setupGlobalInputHandler],
                        api,
                    },
                    context: allContexts,
                }),
        );
    }

    function pushUpdate(): void {
        api.focusable = !hidden;
        $editingInputs = $editingInputs;
    }

    $: {
        hidden;
        pushUpdate();
    }

    onMount(() => {
        $editingInputs.push(api);
        $editingInputs = $editingInputs;

        const unsubscribeFromEditingArea = content.subscribe(writeFromEditingArea);
        const unsubscribeToEditingArea = nodes.subscribe(writeToEditingArea);

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
