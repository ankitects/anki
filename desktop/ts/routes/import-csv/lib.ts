// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { DeckNameId, DeckNames } from "@generated/anki/decks_pb";
import type { CsvMetadata, CsvMetadata_Delimiter, ImportResponse } from "@generated/anki/import_export_pb";
import { type CsvMetadata_MappedNotetype } from "@generated/anki/import_export_pb";
import type { NotetypeNameId, NotetypeNames } from "@generated/anki/notetypes_pb";
import { getCsvMetadata, getFieldNames, importCsv } from "@generated/backend";
import * as tr from "@generated/ftl";
import { cloneDeep, isEqual, noop } from "lodash-es";
import type { Readable, Writable } from "svelte/store";
import { readable, writable } from "svelte/store";

export interface ColumnOption {
    label: string;
    shortLabel?: string;
    value: number;
    disabled: boolean;
}

export function getGlobalNotetype(meta: CsvMetadata): CsvMetadata_MappedNotetype | null {
    return meta.notetype.case === "globalNotetype" ? meta.notetype.value : null;
}

export function getDeckId(meta: CsvMetadata): bigint {
    return meta.deck.case === "deckId" ? meta.deck.value : 0n;
}

export function getDeckName(meta: CsvMetadata): string | null {
    return meta.deck.case === "deckName" ? meta.deck.value : null;
}

export class ImportCsvState {
    readonly path: string;
    readonly deckNameIds: DeckNameId[];
    readonly notetypeNameIds: NotetypeNameId[];

    readonly defaultDelimiter: CsvMetadata_Delimiter;
    readonly defaultIsHtml: boolean;
    readonly defaultNotetypeId: bigint | null;
    readonly defaultDeckId: bigint | null;
    readonly newDeckName: string | null;

    readonly metadata: Writable<CsvMetadata>;
    readonly globalNotetype: Writable<CsvMetadata_MappedNotetype | null>;
    readonly deckId: Writable<bigint | null>;
    readonly fieldNames: Readable<Promise<string[]>>;
    readonly columnOptions: Readable<ColumnOption[]>;

    private lastMetadata: CsvMetadata;
    private lastGlobalNotetype: CsvMetadata_MappedNotetype | null;
    private lastDeckId: bigint | null;
    private fieldNamesSetter: (val: Promise<string[]>) => void = noop;
    private columnOptionsSetter: (val: ColumnOption[]) => void = noop;

    constructor(path: string, notetypes: NotetypeNames, decks: DeckNames, metadata: CsvMetadata) {
        this.path = path;
        this.deckNameIds = decks.entries;
        this.notetypeNameIds = notetypes.entries;

        this.lastMetadata = cloneDeep(metadata);
        this.metadata = writable(metadata);
        this.metadata.subscribe(this.onMetadataChanged.bind(this));

        const globalNotetype = getGlobalNotetype(metadata);
        this.lastGlobalNotetype = cloneDeep(getGlobalNotetype(metadata));
        this.globalNotetype = writable(cloneDeep(globalNotetype));
        this.globalNotetype.subscribe(this.onGlobalNotetypeChanged.bind(this));

        this.lastDeckId = getDeckId(metadata);
        this.deckId = writable(getDeckId(metadata));
        this.deckId.subscribe(this.onDeckIdChanged.bind(this));

        this.fieldNames = readable(
            globalNotetype === null
                ? Promise.resolve([])
                : getFieldNames({ ntid: globalNotetype.id }).then((list) => list.vals),
            (set) => {
                this.fieldNamesSetter = set;
            },
        );

        this.columnOptions = readable(getColumnOptions(metadata), (set) => {
            this.columnOptionsSetter = set;
        });

        this.defaultDelimiter = metadata.delimiter;
        this.defaultIsHtml = metadata.isHtml;
        this.defaultNotetypeId = this.lastGlobalNotetype?.id || null;
        this.defaultDeckId = this.lastDeckId;
        this.newDeckName = getDeckName(metadata);
    }

    doImport(): Promise<ImportResponse> {
        return importCsv({
            path: this.path,
            metadata: { ...this.lastMetadata, preview: [] },
        }, { alertOnError: false });
    }

    private async onMetadataChanged(changed: CsvMetadata) {
        if (isEqual(changed, this.lastMetadata)) {
            return;
        }

        const shouldRefetchMetadata = this.shouldRefetchMetadata(changed);
        if (shouldRefetchMetadata) {
            const { globalTags, updatedTags } = changed;
            changed = await getCsvMetadata({
                path: this.path,
                delimiter: changed.delimiter,
                notetypeId: getGlobalNotetype(changed)?.id,
                deckId: getDeckId(changed) || undefined,
                isHtml: changed.isHtml,
            });
            // carry over tags
            changed.globalTags = globalTags;
            changed.updatedTags = updatedTags;
        }

        const globalNotetype = getGlobalNotetype(changed);
        this.globalNotetype.set(globalNotetype);
        if (globalNotetype !== null && globalNotetype.id !== getGlobalNotetype(this.lastMetadata)?.id) {
            this.fieldNamesSetter(getFieldNames({ ntid: globalNotetype.id }).then((list) => list.vals));
        }
        if (this.shouldRebuildColumnOptions(changed)) {
            this.columnOptionsSetter(getColumnOptions(changed));
        }

        this.lastMetadata = cloneDeep(changed);
        if (shouldRefetchMetadata) {
            this.metadata.set(changed);
        }
    }

    private shouldRefetchMetadata(changed: CsvMetadata): boolean {
        return changed.delimiter !== this.lastMetadata.delimiter || changed.isHtml !== this.lastMetadata.isHtml
            || getGlobalNotetype(changed)?.id !== getGlobalNotetype(this.lastMetadata)?.id;
    }

    private shouldRebuildColumnOptions(changed: CsvMetadata): boolean {
        return !isEqual(changed.columnLabels, this.lastMetadata.columnLabels)
            || !isEqual(changed.preview[0], this.lastMetadata.preview[0]);
    }

    private onGlobalNotetypeChanged(globalNotetype: CsvMetadata_MappedNotetype | null) {
        if (isEqual(globalNotetype, this.lastGlobalNotetype)) {
            return;
        }
        this.lastGlobalNotetype = cloneDeep(globalNotetype);
        if (globalNotetype !== null) {
            this.metadata.update((metadata) => {
                metadata.notetype.value = globalNotetype;
                return metadata;
            });
        }
    }

    private onDeckIdChanged(deckId: bigint | null) {
        if (deckId === this.lastDeckId) {
            return;
        }
        this.lastDeckId = deckId;
        if (deckId !== null) {
            this.metadata.update((metadata) => {
                if (deckId !== 0n) {
                    metadata.deck.case = "deckId";
                    metadata.deck.value = deckId;
                } else {
                    metadata.deck.case = "deckName";
                    metadata.deck.value = this.newDeckName!;
                }
                return metadata;
            });
        }
    }
}

function getColumnOptions(
    metadata: CsvMetadata,
): ColumnOption[] {
    const notetypeColumn = getNotetypeColumn(metadata);
    const deckColumn = getDeckColumn(metadata);
    return [{ label: tr.changeNotetypeNothing(), value: 0, disabled: false }].concat(
        metadata.columnLabels.map((label, index) => {
            index += 1;
            if (index === notetypeColumn) {
                return columnOption(tr.notetypesNotetype(), true, index);
            } else if (index === deckColumn) {
                return columnOption(tr.decksDeck(), true, index);
            } else if (index === metadata.guidColumn) {
                return columnOption("GUID", true, index);
            } else if (label === "") {
                return columnOption(metadata.preview[0].vals[index - 1], false, index, true);
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

function getDeckColumn(meta: CsvMetadata): number | null {
    return meta.deck.case === "deckColumn" ? meta.deck.value : null;
}

function getNotetypeColumn(meta: CsvMetadata): number | null {
    return meta.notetype.case === "notetypeColumn" ? meta.notetype.value : null;
}
