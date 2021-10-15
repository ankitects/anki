<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script context="module" lang="ts">
    import type CustomStyles from "./CustomStyles.svelte";
    import type { EditingInputAPI } from "./EditingArea.svelte";

    export interface EditableAPI extends EditingInputAPI {
        name: "editable";
        moveCaretToEnd(): void;
        toggle(): boolean;
        surround(before: string, after: string): void;
        preventResubscription(): () => void;
    }

    export interface EditableContextAPI {
        styles: CustomStyles;
        container: HTMLElement;
        api: EditableAPI;
    }
</script>

<script lang="ts">
    import EditableStyles from "./EditableStyles.svelte";
    import SetContext from "./SetContext.svelte";
    import ContentEditable from "../editable/ContentEditable.svelte";

    import { onMount, getAllContexts } from "svelte";
    import {
        nodeIsElement,
        nodeContainsInlineContent,
        fragmentToString,
        caretToEnd,
    } from "../lib/dom";
    import { getContext, editableKey, decoratedElementsKey } from "./context";
    import { getEditingArea } from "./EditingArea.svelte";
    import type { EditingAreaAPI } from "./EditingArea.svelte";
    import { promiseResolve } from "../lib/promise";
    import { bridgeCommand } from "../lib/bridgecommand";
    import { wrapInternal } from "../lib/wrap";
    import { nodeStore } from "../sveltelib/node-store";
    import type { DecoratedElement } from "../editable/decorated";

    export let hidden: boolean;

    const { content, editingInputs } = getEditingArea() as EditingAreaAPI;
    const decoratedElements = getContext(decoratedElementsKey);

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

    const [editablePromise, editableResolve] = promiseResolve<HTMLElement>();

    function resolve(editable: HTMLElement): { destroy: () => void } {
        function onPaste(event: Event): void {
            event.preventDefault();
            bridgeCommand("paste");
        }

        function onCutOrCopy(event: Event): void {
            event.preventDefault();
            bridgeCommand("cutOrCopy");
        }

        editable.addEventListener("paste", onPaste);
        editable.addEventListener("copy", onCutOrCopy);
        editable.addEventListener("cut", onCutOrCopy);
        editableResolve(editable);

        return {
            destroy() {
                editable.removeEventListener("paste", onPaste);
                editable.removeEventListener("copy", onCutOrCopy);
                editable.removeEventListener("cut", onCutOrCopy);
            },
        };
    }

    import getDOMMirror from "../sveltelib/mirror-dom";

    const { mirror, preventResubscription } = getDOMMirror();

    function moveCaretToEnd() {
        editablePromise.then(caretToEnd);
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

    export const api: EditableAPI = {
        name: "editable",
        focus() {
            editablePromise.then((editable) => editable.focus());
        },
        moveCaretToEnd,
        focusable: !hidden,
        toggle(): boolean {
            hidden = !hidden;
            return hidden;
        },
        surround(before: string, after: string) {
            editablePromise.then((editable) =>
                wrapInternal(editable.getRootNode() as any, before, after, false)
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

<EditableStyles
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
        {#await Promise.all([editablePromise, stylesPromise]) then [container, styles]}
            <SetContext key={editableKey} value={{ container, styles, api }}>
                <slot />
            </SetContext>
        {/await}
    </div>
</EditableStyles>

<style lang="scss">
    .hidden {
        display: none;
    }
</style>
