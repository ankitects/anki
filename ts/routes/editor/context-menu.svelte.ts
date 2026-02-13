// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { ContextMenu, ContextMenuMouseEvent } from "$lib/context-menu";

import { openMedia, showInMediaFolder } from "@generated/backend";
import * as tr from "@generated/ftl";
import { bridgeCommand } from "@tslib/bridgecommand";
import { getSelection } from "@tslib/cross-browser";
import { get } from "svelte/store";
import type { EditingInputAPI } from "./EditingArea.svelte";
import type { NoteEditorAPI } from "./NoteEditor.svelte";
import { editingInputIsPlainText } from "./plain-text-input";
import { editingInputIsRichText } from "./rich-text-input";
import { writeBlobToClipboard } from "./rich-text-input/data-transfer";
import { EditorState } from "./types";

async function getFieldSelection(focusedInput: EditingInputAPI): Promise<string | null> {
    if (editingInputIsRichText(focusedInput)) {
        const selection = getSelection(await focusedInput.element);
        if (selection && selection.toString()) {
            return selection.toString();
        }
    } else if (editingInputIsPlainText(focusedInput)) {
        const selection = (await focusedInput.codeMirror.editor).getSelection();
        if (selection) {
            return selection;
        }
    }

    return null;
}

function getImageFromMouseEvent(event: ContextMenuMouseEvent, element: HTMLElement): string | null {
    const elements = element.getRootNode().elementsFromPoint(event.clientX, event.clientY);
    for (const element of elements) {
        if (element instanceof HTMLImageElement && (new URL(element.src)).hostname === window.location.hostname) {
            return decodeURI(element.getAttribute("src")!);
        }
    }
    return null;
}

interface ContextMenuItem {
    label: string;
    action: () => void;
}

export function setupContextMenu(): [
    (
        event: ContextMenuMouseEvent,
        noteEditor: NoteEditorAPI,
        focusedInput: EditingInputAPI | null,
        contextMenu: ContextMenu,
    ) => Promise<void>,
    ContextMenuItem[],
] {
    const contextMenuItems: ContextMenuItem[] = $state([]);
    async function onContextMenu(
        event: ContextMenuMouseEvent,
        noteEditor: NoteEditorAPI,
        focusedInput: EditingInputAPI | null,
        contextMenu: ContextMenu,
    ) {
        contextMenuItems.length = 0;
        contextMenuItems.push({
            label: tr.editingPaste(),
            action: () => {
                bridgeCommand("paste");
            },
        });
        const selection = focusedInput ? await getFieldSelection(focusedInput) : null;
        if (selection) {
            contextMenuItems.push({
                label: tr.editingCut(),
                action: () => {
                    bridgeCommand("cut");
                },
            }, {
                label: tr.actionsCopy(),
                action: () => {
                    bridgeCommand("copy");
                },
            });
        }

        let imagePath: string | null = null;
        if (get(noteEditor.state) === EditorState.ImageOcclusionMasks) {
            imagePath = get(noteEditor.lastIOImagePath);
        } else if (focusedInput && editingInputIsRichText(focusedInput)) {
            imagePath = getImageFromMouseEvent(event, await focusedInput.element);
        }
        if (imagePath) {
            contextMenuItems.push({
                label: tr.editingCopyImage(),
                action: async () => {
                    const image = await fetch(imagePath);
                    const blob = await image.blob();
                    await writeBlobToClipboard(blob);
                },
            }, {
                label: tr.editingOpenImage(),
                action: () => {
                    openMedia({ val: imagePath });
                },
            }, {
                label: tr.editingShowInFolder(),
                action: () => {
                    showInMediaFolder({ val: imagePath });
                },
            });
        }

        if (contextMenuItems.length > 0) {
            contextMenu?.show(event);
        }
    }

    return [onContextMenu, contextMenuItems];
}
