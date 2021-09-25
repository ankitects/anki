<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script context="module" lang="ts">
    interface EditableAPI {
        readonly name: string;
        focus(): void;
        moveCaretToEnd(): void;
        fieldHTML: string;
    }
</script>

<script lang="ts">
    import type { DecoratedElement, DecoratedElementConstructor } from "./decorated";
    import { elementContainsInlineContent, caretToEnd } from "lib/dom";
    import { promiseResolve } from "lib/promise";
    import { bridgeCommand } from "lib/bridgecommand";

    export let decoratedComponents: DecoratedElementConstructor[] = [];

    /*  surroundSelection(before: string, after: string): void { */
    /*      wrapInternal(this.getRootNode() as ShadowRoot, before, after, false); */
    /*  } */

    /*  onEnter(event: KeyboardEvent): void { */
    /*      if ( */
    /*          !getBlockElement(this.getRootNode() as Document | ShadowRoot) !== */
    /*          event.shiftKey */
    /*      ) { */
    /*          event.preventDefault(); */
    /*          document.execCommand("insertLineBreak"); */
    /*      } */
    /*  } */

    const [editablePromise, editableResolve] = promiseResolve<HTMLElement>();
    export { editablePromise };

    let editable: HTMLElement;

    function setFieldHTML(content: string) {
        editable.innerHTML = content;

        if (editable.innerHTML.length > 0 && elementContainsInlineContent(editable)) {
            editable.appendChild(document.createElement("br"));
        }
    }

    function getFieldHTML(): string {
        const clone = editable.cloneNode(true) as Element;

        for (const component of decoratedComponents) {
            for (const element of clone.getElementsByTagName(component.tagName)) {
                (element as DecoratedElement).undecorate();
            }
        }

        const result =
            elementContainsInlineContent(clone) && clone.innerHTML.endsWith("<br>")
                ? clone.innerHTML.slice(0, -4) // trim trailing <br>
                : clone.innerHTML;

        return result;
    }

    export const api: EditableAPI = Object.defineProperties(
        {},
        {
            name: { value: "editable" },
            focus: { value: () => editable.focus },
            moveCaretToEnd: { value: () => caretToEnd(editable) },
            fieldHTML: { get: getFieldHTML, set: setFieldHTML },
        }
    );
</script>

<!-- custominput event can be dispatched by inner elements if they want to trigger an update -->
<anki-editable
    bind:this={editable}
    contenteditable="true"
    on:focus
    on:input
    on:custominput
    on:blur
    on:paste|preventDefault={() => bridgeCommand("paste")}
    on:cut|preventDefault={() => bridgeCommand("cutOrCopy")}
    on:copy|preventDefault={() => bridgeCommand("cutOrCopy")}
    use:editableResolve
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
