// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
import { i18n } from "../editor";
import { promiseWithResolver } from "../lib/promise";
import NoteCreator from "./NoteCreator.svelte";

const [uiPromise, uiResolve] = promiseWithResolver();

async function setupNoteCreator(): Promise<void> {
    await i18n;

    new NoteCreator({
        target: document.body,
        props: { uiResolve },
    });
}

setupNoteCreator();

export * from "../editor";
export { uiPromise };
export const noteEditorPromise = uiPromise;
