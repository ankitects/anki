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
    withShortcuts,
} from "editor-toolbar/dynamicComponents";

import boldIcon from "./type-bold.svg";
import italicIcon from "./type-italic.svg";
import underlineIcon from "./type-underline.svg";
import superscriptIcon from "./format-superscript.svg";
import subscriptIcon from "./format-subscript.svg";
import eraserIcon from "./eraser.svg";

export function getFormatInlineGroup(): DynamicSvelteComponent<typeof ButtonGroup> &
    ButtonGroupProps {
    const boldButton = withShortcuts({
        shortcuts: ["Control+KeyB"],
        button: commandIconButton({
            icon: boldIcon,
            tooltip: tr.editingBoldTextCtrlandb(),
            command: "bold",
        }),
    });

    const italicButton = withShortcuts({
        shortcuts: ["Control+KeyI"],
        button: commandIconButton({
            icon: italicIcon,
            tooltip: tr.editingItalicTextCtrlandi(),
            command: "italic",
        }),
    });

    const underlineButton = withShortcuts({
        shortcuts: ["Control+KeyU"],
        button: commandIconButton({
            icon: underlineIcon,
            tooltip: tr.editingUnderlineTextCtrlandu(),
            command: "underline",
        }),
    });

    const superscriptButton = withShortcuts({
        shortcuts: ["Control+Shift+Equal"],
        button: commandIconButton({
            icon: superscriptIcon,
            tooltip: tr.editingSuperscriptCtrlandand(),
            command: "superscript",
        }),
    });

    const subscriptButton = withShortcuts({
        shortcuts: ["Control+Equal"],
        button: commandIconButton({
            icon: subscriptIcon,
            tooltip: tr.editingSubscriptCtrland(),
            command: "subscript",
        }),
    });

    const removeFormatButton = withShortcuts({
        shortcuts: ["Control+KeyR"],
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
