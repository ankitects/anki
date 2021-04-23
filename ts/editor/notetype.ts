// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
import type { IterableToolbarItem } from "editor-toolbar/types";

import { bridgeCommand } from "lib/bridgecommand";
import * as tr from "lib/i18n";
import {
    labelButton,
    buttonGroup,
    withShortcut,
} from "editor-toolbar/dynamicComponents";

export function getNotetypeGroup(): IterableToolbarItem {
    const fieldsButton = labelButton({
        onClick: () => bridgeCommand("fields"),
        disables: false,
        label: `${tr.editingFields()}...`,
        tooltip: tr.editingCustomizeFields(),
    });

    const cardsButton = withShortcut({
        shortcut: "Control+KeyL",
        button: labelButton({
            onClick: () => bridgeCommand("cards"),
            disables: false,
            label: `${tr.editingCards()}...`,
            tooltip: tr.editingCustomizeCardTemplates(),
        }),
    });

    return buttonGroup({
        id: "notetype",
        items: [fieldsButton, cardsButton],
    });
}
