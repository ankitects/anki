// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { CsvMetadata, CsvMetadata_MappedNotetype } from "@tslib/anki/import_export_pb";
import * as tr from "@tslib/ftl";

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

export function tryGetGlobalNotetype(meta: CsvMetadata): CsvMetadata_MappedNotetype | null {
    return meta.notetype.case === "globalNotetype" ? meta.notetype.value : null;
}

export function tryGetDeckId(meta: CsvMetadata): bigint | null {
    return meta.deck.case === "deckId" ? meta.deck.value : null;
}

export function tryGetDeckColumn(meta: CsvMetadata): number | null {
    return meta.deck.case === "deckColumn" ? meta.deck.value : null;
}

export function tryGetNotetypeColumn(meta: CsvMetadata): number | null {
    return meta.notetype.case === "notetypeColumn" ? meta.notetype.value : null;
}

export function buildDeckOneof(
    deckColumn: number | null,
    deckId: bigint | null,
): CsvMetadata["deck"] {
    if (deckColumn !== null) {
        return { case: "deckColumn", value: deckColumn };
    } else if (deckId !== null) {
        return { case: "deckId", value: deckId };
    }
    throw new Error("missing column/id");
}

export function buildNotetypeOneof(
    globalNotetype: CsvMetadata_MappedNotetype | null,
    notetypeColumn: number | null,
): CsvMetadata["notetype"] {
    if (globalNotetype !== null) {
        return { case: "globalNotetype", value: globalNotetype };
    } else if (notetypeColumn !== null) {
        return { case: "notetypeColumn", value: notetypeColumn };
    }
    throw new Error("missing column/id");
}
