// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import "./image-occlusion-base.scss";

import { ModuleName, setupI18n } from "@tslib/i18n";
import { checkNightMode } from "@tslib/nightmode";
import { get } from "svelte/store";

import { addOrUpdateNote } from "./add-or-update-note.svelte";
import ImageOcclusionPage from "./ImageOcclusionPage.svelte";
import type { IOMode } from "./lib";
import { hideAllGuessOne } from "./store";

globalThis.anki = globalThis.anki || {};

const i18n = setupI18n({
    modules: [
        ModuleName.IMPORTING,
        ModuleName.DECKS,
        ModuleName.EDITING,
        ModuleName.NOTETYPES,
        ModuleName.ACTIONS,
        ModuleName.BROWSING,
        ModuleName.UNDO,
    ],
});

export async function setupImageOcclusion(mode: IOMode, target = document.body): Promise<ImageOcclusionPage> {
    checkNightMode();
    await i18n;

    async function addNote(): Promise<void> {
        addOrUpdateNote(mode, get(hideAllGuessOne));
    }

    // for adding note from mobile devices
    globalThis.anki.imageOcclusion = {
        mode,
        addNote,
    };

    return new ImageOcclusionPage({
        target: target,
        props: {
            mode,
        },
    });
}

if (window.location.hash.startsWith("#test-")) {
    const imagePath = window.location.hash.replace("#test-", "");
    setupImageOcclusion({ kind: "add", imagePath, notetypeId: 0n });
}

if (window.location.hash.startsWith("#testforedit-")) {
    const noteId = BigInt(window.location.hash.replace("#testforedit-", ""));
    setupImageOcclusion({ kind: "edit", noteId });
}
