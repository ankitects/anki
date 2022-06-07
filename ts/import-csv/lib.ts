// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import * as tr from "../lib/ftl";
import {
    ImportExport,
    importExport,
    Notetypes,
    notetypes as notetypeService,
} from "../lib/proto";

export interface ColumnOption {
    label: string;
    value: number;
    disabled: boolean;
}

export function getColumnOptions(
    columnLabels: string[],
    notetypeColumn: number | null,
    deckColumn: number | null,
    tagsColumn: number,
    guidColumn: number,
): ColumnOption[] {
    return [{ label: tr.changeNotetypeNothing(), value: 0, disabled: false }].concat(
        columnLabels.map((label, index) => {
            index += 1;
            if (index === notetypeColumn) {
                return columnOption(tr.notetypesNotetype(), true, index);
            } else if (index === deckColumn) {
                return columnOption(tr.decksDeck(), true, index);
            } else if (index === guidColumn) {
                return columnOption("GUID", true, index);
            } else if (index === tagsColumn) {
                return columnOption(tr.editingTags(), false, index);
            } else if (label === "") {
                return columnOption(index, false, index);
            } else {
                return columnOption(`"${label}"`, false, index);
            }
        }),
    );
}

function columnOption(
    label: string | number,
    disabled: boolean,
    index: number,
): ColumnOption {
    return {
        label: tr.importingColumn({ val: label }),
        value: index,
        disabled,
    };
}

export async function getNotetypeFields(notetypeId: number): Promise<string[]> {
    return notetypeService
        .getFieldNames(Notetypes.NotetypeId.create({ ntid: notetypeId }))
        .then((list) => list.vals);
}

export async function getCsvMetadata(
    path: string,
    delimiter?: ImportExport.CsvMetadata.Delimiter,
    notetypeId?: number,
): Promise<ImportExport.CsvMetadata> {
    return importExport.getCsvMetadata(
        ImportExport.CsvMetadataRequest.create({
            path,
            delimiter,
            notetypeId,
        }),
    );
}
