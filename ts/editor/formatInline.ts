// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
import type { IterableToolbarItem } from "editor-toolbar/types";

import * as tr from "lib/i18n";
import {
    commandIconButton,
    iconButton,
    buttonGroup,
    withShortcut,
} from "editor-toolbar/dynamicComponents";

import boldIcon from "./type-bold.svg";
import italicIcon from "./type-italic.svg";
import underlineIcon from "./type-underline.svg";
import superscriptIcon from "./format-superscript.svg";
import subscriptIcon from "./format-subscript.svg";
import eraserIcon from "./eraser.svg";

export function getFormatInlineGroup(): IterableToolbarItem {
    const boldButton = withShortcut({
        shortcut: "Control+KeyB",
        button: commandIconButton({
            icon: boldIcon,
            tooltip: tr.editingBoldText(),
            command: "bold",
        }),
    });

    const italicButton = withShortcut({
        shortcut: "Control+KeyI",
        button: commandIconButton({
            icon: italicIcon,
            tooltip: tr.editingItalicText(),
            command: "italic",
        }),
    });

    const underlineButton = withShortcut({
        shortcut: "Control+KeyU",
        button: commandIconButton({
            icon: underlineIcon,
            tooltip: tr.editingUnderlineText(),
            command: "underline",
        }),
    });

    const superscriptButton = withShortcut({
        shortcut: "Control+Shift+Equal",
        button: commandIconButton({
            icon: superscriptIcon,
            tooltip: tr.editingSuperscript(),
            command: "superscript",
        }),
    });

    const subscriptButton = withShortcut({
        shortcut: "Control+Equal",
        button: commandIconButton({
            icon: subscriptIcon,
            tooltip: tr.editingSubscript(),
            command: "subscript",
        }),
    });

    const removeFormatButton = withShortcut({
        shortcut: "Control+KeyR",
        button: iconButton({
            icon: eraserIcon,
            tooltip: tr.editingRemoveFormatting(),
            onClick: () => {
                document.execCommand("removeFormat");
            },
        }),
    });

    return buttonGroup({
        id: "inlineFormatting",
        items: [
            boldButton,
            italicButton,
            underlineButton,
            superscriptButton,
            subscriptButton,
            removeFormatButton,
        ],
    });
}
