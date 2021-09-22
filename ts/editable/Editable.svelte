<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { getContext, onMount, onDestroy } from "svelte";
    import type { Writable } from "svelte/store";
    import { elementContainsInlineContent, caretToEnd } from "lib/dom";
    import { activeInputKey } from "lib/context-keys";
    import type { DecoratedElement } from "./decorated";
    import { decoratedComponents } from "./decorated";
    import type { ActiveInputAPI } from "editor/EditingArea.svelte";

    export let content: string;
    export let focusOnMount: boolean = false;

    let editable: HTMLElement;

    export function containsInlineContent(): boolean {
        return elementContainsInlineContent(editable);
    }

    /* import type { DecoratedElement } from "./decorated"; */
    /* import { decoratedComponents } from "./decorated"; */
    /* import { bridgeCommand } from "lib/bridgecommand"; */
    /* import { elementIsBlock, getBlockElement } from "lib/dom"; */
    /* import { wrapInternal } from "lib/wrap"; */

    /* function containsInlineContent(element: Element): boolean { */
    /*     for (const child of element.children) { */
    /*         if (elementIsBlock(child) || !containsInlineContent(child)) { */
    /*             return false; */
    /*         } */
    /*     } */

    /*     return true; */
    /* } */

    /* export class Editable extends HTMLElement { */
    /*     surroundSelection(before: string, after: string): void { */
    /*         wrapInternal(this.getRootNode() as ShadowRoot, before, after, false); */
    /*     } */

    /*     onEnter(event: KeyboardEvent): void { */
    /*         if ( */
    /*             !getBlockElement(this.getRootNode() as Document | ShadowRoot) !== */
    /*             event.shiftKey */
    /*         ) { */
    /*             event.preventDefault(); */
    /*             document.execCommand("insertLineBreak"); */
    /*         } */
    /*     } */

    /*     onPaste(event: ClipboardEvent): void { */
    /*         bridgeCommand("paste"); */
    /*         event.preventDefault(); */
    /*     } */
    /* } */

    /* afterUpdate(() => { */
    /*     if (editable && content.length > 0 && containsInlineContent()) { */
    /*         editable.appendChild(document.createElement("br")); */
    /*     } */
    /* }); */

    function autofocus(editable: HTMLElement): void {
        if (focusOnMount) {
            editable.focus();
            caretToEnd(editable);
        }
    }

    const activeInput = getContext<Writable<ActiveInputAPI | null>>(activeInputKey);

    function getFieldHTML(): string {
        const clone = editable.cloneNode(true) as Element;

        for (const component of decoratedComponents) {
            for (const element of clone.getElementsByTagName(component.tagName)) {
                (element as DecoratedElement).undecorate();
            }
        }

        /* TODO expose as api */
        const result =
            elementContainsInlineContent(clone) && clone.innerHTML.endsWith("<br>")
                ? clone.innerHTML.slice(0, -4) // trim trailing <br>
                : clone.innerHTML;

        return result;
    }

    function setFieldHTML(content: string) {
        editable.innerHTML = content;

        if (content.length > 0 && containsInlineContent()) {
            editable.appendChild(document.createElement("br"));
        }
    }

    $activeInput = Object.defineProperties(
        {},
        {
            name: { value: "editable" },
            focus: { value: () => editable.focus },
            moveCaretToEnd: { value: () => caretToEnd(editable) },
            fieldHTML: { get: getFieldHTML, set: setFieldHTML },
        }
    );

    onDestroy(() => ($activeInput = null));
</script>

<anki-editable
    bind:this={editable}
    bind:innerHTML={content}
    contenteditable="true"
    use:autofocus
/>

<style lang="scss">
    anki-editable {
        display: block;
        overflow-wrap: break-word;
        overflow: auto;
        padding: 6px;

        &:focus {
            outline: none;
        }
    }

    /* editable-base.scss contains styling targeting user HTML */
</style>
