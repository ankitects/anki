// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { Empty } from "@generated/anki/generic_pb";
import { ExportAnkiPackageOptions, ExportLimit } from "@generated/anki/import_export_pb";
import { NoteIds } from "@generated/anki/notes_pb";
import {
    exportAnkiPackage,
    exportCardCsv,
    exportNoteCsv,
    showTooltip,
    temporarilyCloseAndExportCollectionPackage,
} from "@generated/backend";
import * as tr from "@generated/ftl";

import type { Exporter, ExportOptions } from "./types";

export function getExporters(withLimit: boolean): Exporter[] {
    return [
        createExporter("colpkg", tr.exportingAnkiCollectionPackage(), {
            showIncludeMedia: true,
            showLegacySupport: true,
            isDefault: !withLimit,
        }, _exportCollectionPackage),
        createExporter("apkg", tr.exportingAnkiDeckPackage(), {
            showDeckList: true,
            showIncludeScheduling: true,
            showIncludeDeckConfigs: true,
            showIncludeMedia: true,
            showLegacySupport: true,
            isDefault: withLimit,
        }, _exportAnkiPackage),
        createExporter("txt", tr.exportingNotesInPlainText(), {
            showDeckList: true,
            showIncludeTags: true,
            showIncludeHtml: true,
            showIncludeDeck: true,
            showIncludeNotetype: true,
            showIncludeGuid: true,
        }, _exportNoteCsv),
        createExporter("txt", tr.exportingCardsInPlainText(), {
            showDeckList: true,
            showIncludeHtml: true,
        }, _exportCardCsv),
    ];
}

export function createExportLimit(deckIdOrNoteIds: bigint | bigint[] | null): ExportLimit {
    if (deckIdOrNoteIds === null) {
        return new ExportLimit({
            limit: {
                case: "wholeCollection",
                value: new Empty(),
            },
        });
    }
    if (Array.isArray(deckIdOrNoteIds)) {
        return new ExportLimit({
            limit: {
                case: "noteIds",
                value: new NoteIds({ noteIds: deckIdOrNoteIds }),
            },
        });
    }
    return new ExportLimit({
        limit: { case: "deckId", value: deckIdOrNoteIds },
    });
}

function createExporter(
    extension: string,
    label: string,
    options: Partial<Exporter>,
    doExport: (outPath: string, limit: ExportLimit, options: ExportOptions) => Promise<void>,
): Exporter {
    return {
        extension,
        label,
        showDeckList: options.showDeckList ?? false,
        showIncludeScheduling: options.showIncludeScheduling ?? false,
        showIncludeDeckConfigs: options.showIncludeDeckConfigs ?? false,
        showIncludeMedia: options.showIncludeMedia ?? false,
        showIncludeTags: options.showIncludeTags ?? false,
        showIncludeHtml: options.showIncludeHtml ?? false,
        showLegacySupport: options.showLegacySupport ?? false,
        showIncludeDeck: options.showIncludeDeck ?? false,
        showIncludeNotetype: options.showIncludeNotetype ?? false,
        showIncludeGuid: options.showIncludeGuid ?? false,
        isDefault: options.isDefault ?? false,
        doExport,
    };
}

async function _exportCollectionPackage(outPath: string, limit: ExportLimit, options: ExportOptions) {
    await temporarilyCloseAndExportCollectionPackage({
        outPath,
        includeMedia: options.includeMedia,
        legacy: options.legacySupport,
    });
    showTooltip({ val: tr.exportingCollectionExported() });
}

async function _exportAnkiPackage(outPath: string, limit: ExportLimit, options: ExportOptions) {
    const result = await exportAnkiPackage({
        outPath,
        limit,
        options: new ExportAnkiPackageOptions({
            withScheduling: options.includeScheduling,
            withDeckConfigs: options.includeDeckConfigs,
            withMedia: options.includeMedia,
            legacy: options.legacySupport,
        }),
    });
    showTooltip({ val: tr.exportingNoteExported({ count: result.val }) });
}

async function _exportNoteCsv(outPath: string, limit: ExportLimit, options: ExportOptions) {
    const result = await exportNoteCsv({
        outPath,
        limit,
        withHtml: options.includeHtml,
        withTags: options.includeTags,
        withDeck: options.includeDeck,
        withNotetype: options.includeNotetype,
        withGuid: options.includeGuid,
    });
    showTooltip({ val: tr.exportingNoteExported({ count: result.val }) });
}

async function _exportCardCsv(outPath: string, limit: ExportLimit, options: ExportOptions) {
    const result = await exportCardCsv({
        outPath,
        limit,
        withHtml: options.includeHtml,
    });
    showTooltip({ val: tr.exportingCardExported({ count: result.val }) });
}
