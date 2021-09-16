// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { on } from "../lib/events";
import { nodeIsText } from "../lib/dom";
import { getSelection } from "../lib/cross-browser";

export type OnInsertCallback = ({ node: Node }) => Promise<void>;

interface InputManager {
    manager(element: Element): { destroy(): void };
    triggerOnInsert(callback: OnInsertCallback): () => void;
}

export function getInputManager(): InputManager {
    const onInsertText: OnInsertCallback[] = [];

    function cancelInsertText(): void {
        onInsertText.length = 0;
    }

    function cancelIfInsertText(event: KeyboardEvent): void {
        if (event.key.length !== 1) {
            cancelInsertText();
        }
    }

    async function onBeforeInput(event: InputEvent): Promise<void> {
        if (event.inputType === "insertText" && onInsertText.length > 0) {
            const nbsp = " ";
            const textContent = event.data === " " ? nbsp : event.data ?? nbsp;
            const node = new Text(textContent);

            const selection = getSelection(event.target! as Node)!;
            const range = selection.getRangeAt(0);

            range.deleteContents();

            if (nodeIsText(range.startContainer) && range.startOffset === 0) {
                const parent = range.startContainer.parentNode!;
                parent.insertBefore(node, range.startContainer);
            } else if (
                nodeIsText(range.endContainer) &&
                range.endOffset === range.endContainer.length
            ) {
                const parent = range.endContainer.parentNode!;
                parent.insertBefore(node, range.endContainer.nextSibling!);
            } else {
                range.insertNode(node);
            }

            range.selectNode(node);
            range.collapse(false);

            for (const callback of onInsertText) {
                await callback({ node });
            }

            event.preventDefault();
        }

        cancelInsertText();
    }

    function manager(element: Element): { destroy(): void } {
        const removeBeforeInput = on(element, "beforeinput", onBeforeInput as any);
        const removePointerDown = on(element, "pointerdown", cancelInsertText);
        const removeBlur = on(element, "blur", cancelInsertText);
        const removeKeyDown = on(
            element,
            "keydown",
            cancelIfInsertText as EventListener,
        );

        return {
            destroy() {
                removeBeforeInput();
                removePointerDown();
                removeBlur();
                removeKeyDown();
            },
        };
    }

    function triggerOnInsert(callback: OnInsertCallback): () => void {
        onInsertText.push(callback);
        return () => {
            const index = onInsertText.indexOf(callback);
            if (index > 0) {
                onInsertText.splice(index, 1);
            }
        };
    }

    return {
        manager,
        triggerOnInsert,
    };
}

export default getInputManager;
