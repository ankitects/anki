<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script context="module" lang="ts">
    import type CustomStyles from "./CustomStyles.svelte";
    import type { EditingInputAPI } from "./EditingArea.svelte";
    import contextProperty from "../sveltelib/context-property";

    export interface RichTextInputAPI extends EditingInputAPI {
        name: "rich-text";
        moveCaretToEnd(): void;
        toggle(): boolean;
        surround(before: string, after: string): void;
        preventResubscription(): () => void;
    }

    export interface RichTextInputContextAPI {
        styles: CustomStyles;
        container: HTMLElement;
        api: RichTextInputAPI;
    }

    const key = Symbol("richText");
    const [set, getRichTextInput, hasRichTextInput] =
        contextProperty<RichTextInputContextAPI>(key);

    export { getRichTextInput, hasRichTextInput };
</script>

<script lang="ts">
    import RichTextStyles from "./RichTextStyles.svelte";
    import SetContext from "./SetContext.svelte";
    import ContentEditable from "../editable/ContentEditable.svelte";

    import { onMount, getAllContexts } from "svelte";
    import {
        nodeIsElement,
        nodeContainsInlineContent,
        fragmentToString,
        caretToEnd,
    } from "../lib/dom";
    import { getDecoratedElements } from "./DecoratedElements.svelte";
    import { getEditingArea } from "./EditingArea.svelte";
    import type { EditingAreaAPI } from "./EditingArea.svelte";
    import { promiseWithResolver } from "../lib/promise";
    import { bridgeCommand } from "../lib/bridgecommand";
    import { wrapInternal } from "../lib/wrap";
    import { nodeStore } from "../sveltelib/node-store";
    import type { DecoratedElement } from "../editable/decorated";

    export let hidden: boolean;

    const { content, editingInputs } = getEditingArea() as EditingAreaAPI;
    const decoratedElements = getDecoratedElements();

    const range = document.createRange();

    function normalizeFragment(fragment: DocumentFragment): void {
        fragment.normalize();

        for (const decorated of decoratedElements) {
            for (const element of fragment.querySelectorAll(
                decorated.tagName
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
        /* we need createContextualFragment so that customElements are initialized */
        const fragment = range.createContextualFragment(adjustInputHTML(html));
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

    function attachShadow(element: Element): void {
        element.attachShadow({ mode: "open" });
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

        richTextInput.addEventListener("paste", onPaste);
        richTextInput.addEventListener("copy", onCutOrCopy);
        richTextInput.addEventListener("cut", onCutOrCopy);
        richTextResolve(richTextInput);

        return {
            destroy() {
                richTextInput.removeEventListener("paste", onPaste);
                richTextInput.removeEventListener("copy", onCutOrCopy);
                richTextInput.removeEventListener("cut", onCutOrCopy);
            },
        };
    }

    import getDOMMirror from "../sveltelib/mirror-dom";

    const { mirror, preventResubscription } = getDOMMirror();

    function moveCaretToEnd() {
        richTextPromise.then(caretToEnd);
    }

    const allContexts = getAllContexts();

    function attachContentEditable(element: Element, { stylesDidLoad }): void {
        stylesDidLoad.then(() => {
            const contentEditable = new ContentEditable({
                target: element.shadowRoot!,
                props: {
                    nodes,
                    resolve,
                    mirror,
                },
                context: allContexts,
            });

            contentEditable.$on("focus", moveCaretToEnd);
        });
    }

    export const api: RichTextInputAPI = {
        name: "rich-text",
        focus() {
            richTextPromise.then((richText) => richText.focus());
        },
        moveCaretToEnd,
        focusable: !hidden,
        toggle(): boolean {
            hidden = !hidden;
            return hidden;
        },
        surround(before: string, after: string) {
            richTextPromise.then((richText) =>
                wrapInternal(richText.getRootNode() as any, before, after, false)
            );
        },
        preventResubscription,
    };

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

<RichTextStyles
    color="white"
    let:attachToShadow={attachStyles}
    let:promise={stylesPromise}
    let:stylesDidLoad
>
    <div
        class:hidden
        use:attachShadow
        use:attachStyles
        use:attachContentEditable={{ stylesDidLoad }}
        on:focusin
        on:focusout
    />

    <div class="editable-widgets">
        {#await Promise.all([richTextPromise, stylesPromise]) then [container, styles]}
            <SetContext setter={set} value={{ container, styles, api }}>
                <slot />
            </SetContext>
        {/await}
    </div>
</RichTextStyles>

<style lang="scss">
    .hidden {
        display: none;
    }
</style>
