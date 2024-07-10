// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { ExportLimit } from "@generated/anki/import_export_pb";

export interface Exporter {
    extension: string;
    label: string;
    showDeckList: boolean;
    showIncludeScheduling: boolean;
    showIncludeDeckConfigs: boolean;
    showIncludeMedia: boolean;
    showIncludeTags: boolean;
    showIncludeHtml: boolean;
    showLegacySupport: boolean;
    showIncludeDeck: boolean;
    showIncludeNotetype: boolean;
    showIncludeGuid: boolean;
    isDefault: boolean;
    doExport: (outPath: string, limit: ExportLimit, options: ExportOptions) => Promise<void>;
}

export interface Limit {
    extension: string;
    label: string;
    showDeckList: boolean;
    showIncludeScheduling: boolean;
    showIncludeDeckConfigs: boolean;
    showIncludeMedia: boolean;
    showIncludeTags: boolean;
    showIncludeHtml: boolean;
    showLegacySupport: boolean;
    showIncludeDeck: boolean;
    showIncludeNotetype: boolean;
    showIncludeGuid: boolean;
    isDefault: boolean;
}

export type LimitValue = bigint | bigint[] | null;

export interface LimitChoice {
    label: string;
    value: number;
    limit: LimitValue;
}

export interface ExportOptions {
    includeScheduling: boolean;
    includeDeckConfigs: boolean;
    includeMedia: boolean;
    includeTags: boolean;
    includeHtml: boolean;
    legacySupport: boolean;
    includeDeck: boolean;
    includeNotetype: boolean;
    includeGuid: boolean;
}
