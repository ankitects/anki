// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import * as tr from "@tslib/ftl";
import { ImportExport, importExport, Notetypes, notetypes as notetypeService } from "@tslib/proto";

export interface ColumnOption {
    label: string;
    shortLabel?: string;
    value: number;
    disabled: boolean;
}

export function getColumnOptions(
    columnLabels: string[],
    firstRow: string[],
    notetypeColumn: number | null,
    deckColumn: number | null,
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
            } else if (label === "") {
                return columnOption(firstRow[index - 1], false, index, true);
            } else {
                return columnOption(label, false, index);
            }
        }),
    );
}

function columnOption(
    label: string,
    disabled: boolean,
    index: number,
    shortLabel?: boolean,
): ColumnOption {
    return {
        label: label ? `${index}: ${label}` : index.toString(),
        shortLabel: shortLabel ? index.toString() : undefined,
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
    isHtml?: boolean,
): Promise<ImportExport.CsvMetadata> {
    return importExport.getCsvMetadata(
        ImportExport.CsvMetadataRequest.create({
            path,
            delimiter,
            notetypeId,
            isHtml,
        }),
    );
}
