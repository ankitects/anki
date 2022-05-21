// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import * as tr from "../lib/ftl";
import {
    ImportExport,
    importExport,
    Notetypes,
    notetypes as notetypeService,
} from "../lib/proto";

export function buildColumnOptions(
    columnLabels: string[],
    notetypeColumn: number | null,
    deckColumn: number | null,
): string[] {
    return [tr.changeNotetypeNothing()].concat(
        columnLabels
            .map((label, index) => {
                if (index === notetypeColumn) {
                    return tr.notetypesNotetype();
                } else if (index === deckColumn) {
                    return tr.decksDeck();
                } else if (label === "") {
                    return index + 1;
                } else {
                    return `"${label}"`;
                }
            })
            .map((label) => tr.importingColumn({ val: label })),
    );
}

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
