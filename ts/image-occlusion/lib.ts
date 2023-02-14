// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { Collection } from "../lib/proto";
import { ImageOcclusion, imageOcclusion } from "../lib/proto";

export async function getImageClozeMetadata(
    path: string,
): Promise<ImageOcclusion.ImageClozeMetadata> {
    return imageOcclusion.getImageClozeMetadata(
        ImageOcclusion.ImageClozeMetadataRequest.create({
            path,
        }),
    );
}

export async function addImageOcclusionNotes(
    imagePath: string,
    occlusions: string,
    header: string,
    backExtra: string,
    tags: string[],
): Promise<Collection.OpChanges> {
    return imageOcclusion.addImageOcclusionNotes(
        ImageOcclusion.AddImageOcclusionNotesRequest.create({
            imagePath,
            occlusions,
            header,
            backExtra,
            tags,
        }),
    );
}

export async function getImageClozeNotes(
    noteId: number,
): Promise<ImageOcclusion.ImageClozeNote> {
    return imageOcclusion.getImageClozeNotes(
        ImageOcclusion.GetImageOcclusionNotesRequest.create({
            noteId,
        }),
    );
}

export async function updateImageOcclusionNotes(
    noteId: number,
    occlusions: string,
    header: string,
    backExtra: string,
    tags: string[],
): Promise<Collection.OpChanges> {
    return imageOcclusion.updateImageOcclusionNotes(
        ImageOcclusion.UpdateImageOcclusionNotesRequest.create({
            noteId,
            occlusions,
            header,
            backExtra,
            tags,
        }),
    );
}
