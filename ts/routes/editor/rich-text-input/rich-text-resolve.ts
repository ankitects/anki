// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { on } from "@tslib/events";
import { promiseWithResolver } from "@tslib/promise";
import { handleCutOrCopy, handleDragover, handleDrop, handleKeydown, handlePaste } from "./data-transfer";

function bridgeCopyPasteCommands(input: HTMLElement, isLegacy: boolean): { destroy(): void } {
    const removePaste = on(input, "paste", (evt) => handlePaste(evt, isLegacy));
    const removeCopy = on(input, "copy", (evt) => handleCutOrCopy(evt, isLegacy));
    const removeCut = on(input, "cut", (evt) => handleCutOrCopy(evt, isLegacy));
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

function useRichTextResolve(isLegacy: boolean): [Promise<HTMLElement>, (input: HTMLElement) => void] {
    const [promise, resolve] = promiseWithResolver<HTMLElement>();

    function richTextResolve(input: HTMLElement): { destroy(): void } {
        const destroy = bridgeCopyPasteCommands(input, isLegacy);
        resolve(input);
        return destroy;
    }

    return [promise, richTextResolve];
}

export default useRichTextResolve;
