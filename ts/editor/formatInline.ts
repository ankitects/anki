// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
import type ButtonGroup from "editor-toolbar/ButtonGroup.svelte";
import type { ButtonGroupProps } from "editor-toolbar/ButtonGroup";
import type { DynamicSvelteComponent } from "sveltelib/dynamicComponent";

import * as tr from "anki/i18n";
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

export function getFormatInlineGroup(): DynamicSvelteComponent<typeof ButtonGroup> &
    ButtonGroupProps {
    const boldButton = withShortcut({
        shortcut: "Control+KeyB",
        button: commandIconButton({
            icon: boldIcon,
            tooltip: tr.editingBoldTextCtrlandb(),
            command: "bold",
        }),
    });

    const italicButton = withShortcut({
        shortcut: "Control+KeyI",
        button: commandIconButton({
            icon: italicIcon,
            tooltip: tr.editingItalicTextCtrlandi(),
            command: "italic",
        }),
    });

    const underlineButton = withShortcut({
        shortcut: "Control+KeyU",
        button: commandIconButton({
            icon: underlineIcon,
            tooltip: tr.editingUnderlineTextCtrlandu(),
            command: "underline",
        }),
    });

    const superscriptButton = commandIconButton({
        icon: superscriptIcon,
        tooltip: tr.editingSuperscriptCtrlandand(),
        command: "superscript",
    });

    const subscriptButton = commandIconButton({
        icon: subscriptIcon,
        tooltip: tr.editingSubscriptCtrland(),
        command: "subscript",
    });

    const removeFormatButton = withShortcut({
        shortcut: "Control+KeyR",
        button: iconButton({
            icon: eraserIcon,
            tooltip: tr.editingRemoveFormattingCtrlandr(),
            onClick: () => {
                document.execCommand("removeFormat");
            },
        }),
    });

    return buttonGroup({
        id: "inlineFormatting",
        buttons: [
            boldButton,
            italicButton,
            underlineButton,
            superscriptButton,
            subscriptButton,
            removeFormatButton,
        ],
    });
}
