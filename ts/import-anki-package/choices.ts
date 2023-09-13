// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { ImportAnkiPackageUpdateCondition } from "@tslib/anki/import_export_pb";
import * as tr from "@tslib/ftl";
import type { Choice } from "components/EnumSelector.svelte";

export function updateChoices(): Choice<ImportAnkiPackageUpdateCondition>[] {
    return [
        {
            label: tr.importingUpdateIfNewer(),
            value: ImportAnkiPackageUpdateCondition.IF_NEWER,
        },
        {
            label: tr.importingUpdateAlways(),
            value: ImportAnkiPackageUpdateCondition.ALWAYS,
        },
        {
            label: tr.importingUpdateNever(),
            value: ImportAnkiPackageUpdateCondition.NEVER,
        },
    ];
}
