<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script context="module" lang="ts">
    import type CustomStyles from "./CustomStyles.svelte";
    import type { EditingInputAPI } from "../EditingArea.svelte";
    import type { ContentEditableAPI } from "../../editable/ContentEditable.svelte";
    import contextProperty from "../../sveltelib/context-property";
    import type {
        Trigger,
        OnInsertCallback,
        OnInputCallback,
    } from "../../sveltelib/input-manager";
    import { pageTheme } from "../../sveltelib/theme";

    export interface RichTextInputAPI extends EditingInputAPI, ContentEditableAPI {
        name: "rich-text";
        shadowRoot: Promise<ShadowRoot>;
        element: Promise<HTMLElement>;
        moveCaretToEnd(): void;
        refocus(): void;
        toggle(): boolean;
        preventResubscription(): () => void;
        getTriggerOnNextInsert(): Trigger<OnInsertCallback>;
        getTriggerOnInput(): Trigger<OnInputCallback>;
        getTriggerAfterInput(): Trigger<OnInputCallback>;
    }

    export interface RichTextInputContextAPI {
        styles: CustomStyles;
        container: HTMLElement;
        api: RichTextInputAPI;
    }

    const key = Symbol("richText");
    const {
        setContextProperty,
        get: getRichTextInput,
        has: hasRichTextInput,
    } = contextProperty<RichTextInputContextAPI>(key);

    export { getRichTextInput, hasRichTextInput };

    import getDOMMirror from "../../sveltelib/mirror-dom";
    import getInputManager from "../../sveltelib/input-manager";

    const {
        manager: globalInputManager,
        getTriggerAfterInput,
        getTriggerOnInput,
        getTriggerOnNextInsert,
    } = getInputManager();

    export { getTriggerAfterInput, getTriggerOnInput, getTriggerOnNextInsert };
</script>

<script lang="ts">
    import { onMount, getAllContexts } from "svelte";
    import {
        nodeIsElement,
        nodeContainsInlineContent,
        fragmentToString,
    } from "../../lib/dom";
    import ContentEditable from "../../editable/ContentEditable.svelte";
    import { placeCaretAfterContent } from "../../domlib/place-caret";
    import { getDecoratedElements } from "../DecoratedElements.svelte";
    import { getEditingArea } from "../EditingArea.svelte";
    import { promiseWithResolver } from "../../lib/promise";
    import { bridgeCommand } from "../../lib/bridgecommand";
    import { on } from "../../lib/events";
    import { nodeStore } from "../../sveltelib/node-store";
    import type { DecoratedElement } from "../../editable/decorated";
    import RichTextStyles from "./RichTextStyles.svelte";
    import SetContext from "./SetContext.svelte";

    export let hidden: boolean;

    const { content, editingInputs } = getEditingArea();
    const decoratedElements = getDecoratedElements();

    const range = document.createRange();

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

    const { mirror, preventResubscription } = getDOMMirror();
    const localInputManager = getInputManager();

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
            });
        },
        focusable: !hidden,
        toggle(): boolean {
            hidden = !hidden;
            return hidden;
        },
        moveCaretToEnd,
        preventResubscription,
        getTriggerOnNextInsert: localInputManager.getTriggerOnNextInsert,
        getTriggerOnInput: localInputManager.getTriggerOnInput,
        getTriggerAfterInput: localInputManager.getTriggerAfterInput,
    } as RichTextInputAPI;

    const allContexts = getAllContexts();

    function attachContentEditable(element: Element, { stylesDidLoad }): void {
        stylesDidLoad.then(
            () =>
                new ContentEditable({
                    target: element.shadowRoot!,
                    props: {
                        nodes,
                        resolve,
                        mirrors: [mirror],
                        managers: [globalInputManager, localInputManager.manager],
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

<div class="rich-text-input">
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
