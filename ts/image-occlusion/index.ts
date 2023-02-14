// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import "./image-occlusion-base.scss";

import { ModuleName, setupI18n } from "@tslib/i18n";

import { checkNightMode } from "../lib/nightmode";
import ImageOcclusionPage from "./ImageOcclusionPage.svelte";

const i18n = setupI18n({
    modules: [
        ModuleName.IMPORTING,
        ModuleName.DECKS,
        ModuleName.EDITING,
        ModuleName.NOTETYPES,
        ModuleName.ACTIONS,
        ModuleName.BROWSING,
    ],
});

export async function setupImageOcclusion(path: string): Promise<ImageOcclusionPage> {
    checkNightMode();
    await i18n;

    return new ImageOcclusionPage({
        target: document.body,
        props: {
            path: path,
            noteId: null,
        },
    });
}

export async function setupImageOcclusionForEdit(noteId: number): Promise<ImageOcclusionPage> {
    checkNightMode();
    await i18n;

    return new ImageOcclusionPage({
        target: document.body,
        props: {
            path: null,
            noteId: noteId,
        },
    });
}

if (window.location.hash.startsWith("#test-")) {
    const path = window.location.hash.replace("#test-", "");
    setupImageOcclusion(path);
}

if (window.location.hash.startsWith("#testforedit-")) {
    const noteId = parseInt(window.location.hash.replace("#testforedit-", ""));
    setupImageOcclusionForEdit(noteId);
}
