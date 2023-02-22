// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { Collection } from "../lib/proto";
import { ImageOcclusion, imageOcclusion } from "../lib/proto";

export async function getImageForOcclusion(
    path: string,
): Promise<ImageOcclusion.ImageData> {
    return imageOcclusion.getImageForOcclusion(
        ImageOcclusion.GetImageForOcclusionRequest.create({
            path,
        }),
    );
}

export async function addImageOcclusionNote(
    imagePath: string,
    occlusions: string,
    header: string,
    backExtra: string,
    tags: string[],
): Promise<Collection.OpChanges> {
    return imageOcclusion.addImageOcclusionNote(
        ImageOcclusion.AddImageOcclusionNoteRequest.create({
            imagePath,
            occlusions,
            header,
            backExtra,
            tags,
        }),
    );
}

export async function getImageClozeNote(
    noteId: number,
): Promise<ImageOcclusion.ImageClozeNoteResponse> {
    return imageOcclusion.getImageClozeNote(
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
