// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { on } from "@tslib/events";
import { promiseWithResolver } from "@tslib/promise";
import { handleCutOrCopy, handleDragover, handleDrop, handleKeydown, handlePaste } from "./data-transfer";

function bridgeCopyPasteCommands(input: HTMLElement): { destroy(): void } {
    const removePaste = on(input, "paste", handlePaste);
    const removeCopy = on(input, "copy", handleCutOrCopy);
    const removeCut = on(input, "cut", handleCutOrCopy);
    const removeDragover = on(input, "dragover", handleDragover);
    const removeDrop = on(input, "drop", handleDrop);
    const removeKeydown = on(input, "keydown", handleKeydown);
    return {
        destroy() {
            removePaste();
            removeCopy();
            removeCut();
            removeDragover();
            removeDrop();
            removeKeydown();
        },
    };
}

function useRichTextResolve(): [Promise<HTMLElement>, (input: HTMLElement) => void] {
    const [promise, resolve] = promiseWithResolver<HTMLElement>();

    function richTextResolve(input: HTMLElement): { destroy(): void } {
        const destroy = bridgeCopyPasteCommands(input);
        resolve(input);
        return destroy;
    }

    return [promise, richTextResolve];
}

export default useRichTextResolve;
