// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
import CommandIconButton from "editor-toolbar/CommandIconButton.svelte";
import type { CommandIconButtonProps } from "editor-toolbar/CommandIconButton";
import IconButton from "editor-toolbar/IconButton.svelte";
import type { IconButtonProps } from "editor-toolbar/IconButton";
import ButtonGroup from "editor-toolbar/ButtonGroup.svelte";
import type { ButtonGroupProps } from "editor-toolbar/ButtonGroup";

import { DynamicSvelteComponent, dynamicComponent } from "sveltelib/dynamicComponent";
import * as tr from "anki/i18n";

import boldIcon from "./type-bold.svg";
import italicIcon from "./type-italic.svg";
import underlineIcon from "./type-underline.svg";
import superscriptIcon from "./format-superscript.svg";
import subscriptIcon from "./format-subscript.svg";
import eraserIcon from "./eraser.svg";

const commandIconButton = dynamicComponent<
    typeof CommandIconButton,
    CommandIconButtonProps
>(CommandIconButton);
const iconButton = dynamicComponent<typeof IconButton, IconButtonProps>(IconButton);
const buttonGroup = dynamicComponent<typeof ButtonGroup, ButtonGroupProps>(ButtonGroup);

export function getFormatInlineGroup(): DynamicSvelteComponent<typeof ButtonGroup> &
    ButtonGroupProps {
    const boldButton = commandIconButton({
        icon: boldIcon,
        tooltip: tr.editingBoldTextCtrlandb(),
        command: "bold",
    });

    const italicButton = commandIconButton({
        icon: italicIcon,
        tooltip: tr.editingItalicTextCtrlandi(),
        command: "italic",
    });

    const underlineButton = commandIconButton({
        icon: underlineIcon,
        tooltip: tr.editingUnderlineTextCtrlandu(),
        command: "underline",
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

    const removeFormatButton = iconButton({
        icon: eraserIcon,
        tooltip: tr.editingRemoveFormattingCtrlandr(),
        onClick: () => {
            document.execCommand("removeFormat");
        },
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
