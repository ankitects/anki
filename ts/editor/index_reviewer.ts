// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
import { globalExport } from "@tslib/globals";
import { setupI18n } from "@tslib/i18n";
import { uiResolve } from "@tslib/ui";

import { editorModules } from "./base";
import ReviewerEditor from "./ReviewerEditor.svelte";

async function setupReviewerEditor(): Promise<void> {
    await setupI18n({ modules: editorModules });

    new ReviewerEditor({
        target: document.body,
        props: { uiResolve },
    });
}

setupReviewerEditor();

import * as base from "./base";

globalExport(base);
