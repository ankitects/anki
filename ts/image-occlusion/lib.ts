// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { Collection } from "../lib/proto";
import { ImageOcclusion, imageOcclusion } from "../lib/proto";

export interface IOAddingMode {
    kind: "add";
    notetypeId: number;
    imagePath: string;
}

export interface IOEditingMode {
    kind: "edit";
    noteId: number;
}

export type IOMode = IOAddingMode | IOEditingMode;

export async function getImageForOcclusion(
    path: string,
): Promise<ImageOcclusion.GetImageForOcclusionResponse> {
    return imageOcclusion.getImageForOcclusion(
        ImageOcclusion.GetImageForOcclusionRequest.create({
            path,
        }),
    );
}

export async function addImageOcclusionNote(
    notetypeId: number,
    imagePath: string,
    occlusions: string,
    header: string,
    backExtra: string,
    tags: string[],
): Promise<Collection.OpChanges> {
    return imageOcclusion.addImageOcclusionNote(
        ImageOcclusion.AddImageOcclusionNoteRequest.create({
            notetypeId,
            imagePath,
            occlusions,
            header,
            backExtra,
            tags,
        }),
    );
}

export async function getImageOcclusionNote(
    noteId: number,
): Promise<ImageOcclusion.GetImageOcclusionNoteResponse> {
    return imageOcclusion.getImageOcclusionNote(
        ImageOcclusion.GetImageOcclusionNoteRequest.create({
            noteId,
        }),
    );
}

export async function updateImageOcclusionNote(
    noteId: number,
    occlusions: string,
    header: string,
    backExtra: string,
    tags: string[],
): Promise<Collection.OpChanges> {
    return imageOcclusion.updateImageOcclusionNote(
        ImageOcclusion.UpdateImageOcclusionNoteRequest.create({
            noteId,
            occlusions,
            header,
            backExtra,
            tags,
        }),
    );
}
