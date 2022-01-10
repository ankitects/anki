// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
import { i18n } from "../editor";
import { promiseWithResolver } from "../lib/promise";
import ReviewerEditor from "./ReviewerEditor.svelte";

const [uiPromise, uiResolve] = promiseWithResolver();

async function setupReviewerEditor(): Promise<void> {
    await i18n;

    new ReviewerEditor({
        target: document.body,
        props: { uiResolve },
    });
}

setupReviewerEditor();

export * from "../editor";
export { uiPromise };
export const noteEditorPromise = uiPromise;
