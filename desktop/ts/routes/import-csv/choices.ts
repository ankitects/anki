// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import {
    CsvMetadata_Delimiter,
    CsvMetadata_DupeResolution,
    CsvMetadata_MatchScope,
} from "@generated/anki/import_export_pb";
import * as tr from "@generated/ftl";

import type { Choice } from "$lib/components/EnumSelector.svelte";

export function delimiterChoices(): Choice<CsvMetadata_Delimiter>[] {
    return [
        {
            label: tr.importingTab(),
            value: CsvMetadata_Delimiter.TAB,
        },
        {
            label: tr.importingPipe(),
            value: CsvMetadata_Delimiter.PIPE,
        },
        {
            label: tr.importingSemicolon(),
            value: CsvMetadata_Delimiter.SEMICOLON,
        },
        {
            label: tr.importingColon(),
            value: CsvMetadata_Delimiter.COLON,
        },
        {
            label: tr.importingComma(),
            value: CsvMetadata_Delimiter.COMMA,
        },
        {
            label: tr.studyingSpace(),
            value: CsvMetadata_Delimiter.SPACE,
        },
    ];
}

export function dupeResolutionChoices(): Choice<CsvMetadata_DupeResolution>[] {
    return [
        { label: tr.importingUpdate(), value: CsvMetadata_DupeResolution.UPDATE },
        { label: tr.importingPreserve(), value: CsvMetadata_DupeResolution.PRESERVE },
        { label: tr.importingDuplicate(), value: CsvMetadata_DupeResolution.DUPLICATE },
    ];
}

export function matchScopeChoices(): Choice<CsvMetadata_MatchScope>[] {
    return [
        { label: tr.notetypesNotetype(), value: CsvMetadata_MatchScope.NOTETYPE },
        { label: tr.importingNotetypeAndDeck(), value: CsvMetadata_MatchScope.NOTETYPE_AND_DECK },
    ];
}
