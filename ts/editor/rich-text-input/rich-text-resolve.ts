// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { bridgeCommand } from "../../lib/bridgecommand";
import { on } from "../../lib/events";
import { promiseWithResolver } from "../../lib/promise";

function bridgeCopyPasteCommands(input: HTMLElement): { destroy(): void } {
    function onPaste(event: Event): void {
        event.preventDefault();
        bridgeCommand("paste");
    }

    function onCutOrCopy(): void {
        bridgeCommand("cutOrCopy");
    }

    const removePaste = on(input, "paste", onPaste);
    const removeCopy = on(input, "copy", onCutOrCopy);
    const removeCut = on(input, "cut", onCutOrCopy);

    return {
        destroy() {
            removePaste();
            removeCopy();
            removeCut();
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
