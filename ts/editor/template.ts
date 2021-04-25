// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
import type { IterableToolbarItem } from "sveltelib/types";

import { bridgeCommand } from "lib/bridgecommand";

import { dropdownMenu, dropdownItem, buttonGroup } from "sveltelib/dynamicComponents";

import {
    iconButton,
    withDropdownMenu,
    withShortcut,
} from "editor-toolbar/dynamicComponents";
import * as tr from "lib/i18n";

import { wrap } from "./wrap";

import paperclipIcon from "./paperclip.svg";
import micIcon from "./mic.svg";
import functionIcon from "./function-variant.svg";
import xmlIcon from "./xml.svg";

import { getClozeButton } from "./cloze";

function onAttachment(): void {
    bridgeCommand("attach");
}

function onRecord(): void {
    bridgeCommand("record");
}

function onHtmlEdit(): void {
    bridgeCommand("htmlEdit");
}

const mathjaxMenuId = "mathjaxMenu";

export function getTemplateGroup(): IterableToolbarItem {
    const attachmentButton = withShortcut({
        shortcut: "F3",
        button: iconButton({
            icon: paperclipIcon,
            onClick: onAttachment,
            tooltip: tr.editingAttachPicturesaudiovideo(),
        }),
    });

    const recordButton = withShortcut({
        shortcut: "F5",
        button: iconButton({
            icon: micIcon,
            onClick: onRecord,
            tooltip: tr.editingRecordAudio(),
        }),
    });

    const mathjaxButton = iconButton({
        icon: functionIcon,
    });

    const mathjaxButtonWithMenu = withDropdownMenu({
        button: mathjaxButton,
        menuId: mathjaxMenuId,
    });

    const htmlButton = withShortcut({
        shortcut: "Control+Shift+KeyX",
        button: iconButton({
            icon: xmlIcon,
            onClick: onHtmlEdit,
            tooltip: tr.editingHtmlEditor(),
        }),
    });

    return buttonGroup({
        id: "template",
        items: [
            attachmentButton,
            recordButton,
            getClozeButton(),
            mathjaxButtonWithMenu,
            htmlButton,
        ],
    });
}

export function getTemplateMenus(): IterableToolbarItem[] {
    const mathjaxMenuItems = [
        withShortcut({
            shortcut: "Control+KeyM, KeyM",
            button: dropdownItem({
                onClick: () => wrap("\\(", "\\)"),
                label: tr.editingMathjaxInline(),
            }),
        }),
        withShortcut({
            shortcut: "Control+KeyM, KeyE",
            button: dropdownItem({
                onClick: () => wrap("\\[", "\\]"),
                label: tr.editingMathjaxBlock(),
            }),
        }),
        withShortcut({
            shortcut: "Control+KeyM, KeyC",
            button: dropdownItem({
                onClick: () => wrap("\\(\\ce{", "}\\)"),
                label: tr.editingMathjaxChemistry(),
            }),
        }),
    ];

    const latexMenuItems = [
        withShortcut({
            shortcut: "Control+KeyT, KeyT",
            button: dropdownItem({
                onClick: () => wrap("[latex]", "[/latex]"),
                label: tr.editingLatex(),
            }),
        }),
        withShortcut({
            shortcut: "Control+KeyT, KeyE",
            button: dropdownItem({
                onClick: () => wrap("[$]", "[/$]"),
                label: tr.editingLatexEquation(),
            }),
        }),
        withShortcut({
            shortcut: "Control+KeyT, KeyM",
            button: dropdownItem({
                onClick: () => wrap("[$$]", "[/$$]"),
                label: tr.editingLatexMathEnv(),
            }),
        }),
    ];

    const mathjaxMenu = dropdownMenu({
        id: mathjaxMenuId,
        items: [...mathjaxMenuItems, ...latexMenuItems],
    });

    return [mathjaxMenu];
}
