import CommandIconButton from "./CommandIconButton.svelte";
import type { CommandIconButtonProps } from "./CommandIconButton";

import { dynamicComponent } from "sveltelib/dynamicComponent";
import * as tr from "anki/i18n";

import boldIcon from "./type-bold.svg";
import italicIcon from "./type-italic.svg";
import underlineIcon from "./type-underline.svg";
import superscriptIcon from "./format-superscript.svg";
import subscriptIcon from "./format-subscript.svg";
import eraserIcon from "./eraser.svg";

const commandIconButton = dynamicComponent(CommandIconButton);

const boldButton = commandIconButton<CommandIconButtonProps, "tooltip">(
    {
        icon: boldIcon,
        command: "bold",
    },
    {
        tooltip: tr.editingBoldTextCtrlandb,
    }
);

const italicButton = commandIconButton<CommandIconButtonProps, "tooltip">(
    {
        icon: italicIcon,
        command: "italic",
    },
    {
        tooltip: tr.editingItalicTextCtrlandi,
    }
);

const underlineButton = commandIconButton<CommandIconButtonProps, "tooltip">(
    {
        icon: underlineIcon,
        command: "underline",
    },
    {
        tooltip: tr.editingUnderlineTextCtrlandu,
    }
);

const superscriptButton = commandIconButton<CommandIconButtonProps, "tooltip">(
    {
        icon: superscriptIcon,
        command: "superscript",
    },
    {
        tooltip: tr.editingSuperscriptCtrlandand,
    }
);

const subscriptButton = commandIconButton<CommandIconButtonProps, "tooltip">(
    {
        icon: subscriptIcon,
        command: "subscript",
    },
    {
        tooltip: tr.editingSubscriptCtrland,
    }
);

const removeFormatButton = commandIconButton<CommandIconButtonProps, "tooltip">(
    {
        icon: eraserIcon,
        command: "removeFormat",
        activatable: false,
    },
    {
        tooltip: tr.editingRemoveFormattingCtrlandr,
    }
);

export const formatButtons = [
    boldButton,
    italicButton,
    underlineButton,
    superscriptButton,
    subscriptButton,
    removeFormatButton,
];
