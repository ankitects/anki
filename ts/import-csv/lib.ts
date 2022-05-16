// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import {
    ImportExport,
    importExport,
    Notetypes,
    notetypes as notetypeService,
} from "../lib/proto";

export async function getNotetypeFields(notetypeId: number): Promise<string[]> {
    return notetypeService
        .getFieldNames(Notetypes.NotetypeId.create({ ntid: notetypeId }))
        .then((list) => list.vals);
}

export async function getCsvMetadata(
    path: string,
    delimiter?: ImportExport.CsvMetadata.Delimiter,
): Promise<ImportExport.CsvMetadata> {
    return importExport.getCsvMetadata(
        ImportExport.CsvMetadataRequest.create({
            path,
            delimiter,
        }),
    );
}
