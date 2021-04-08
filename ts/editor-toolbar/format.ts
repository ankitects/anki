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

const boldButton = commandIconButton<CommandIconButtonProps, "title">(
    {
        icon: boldIcon,
        command: "bold",
    },
    {
        title: tr.editingBoldTextCtrlandb,
    }
);

const italicButton = commandIconButton<CommandIconButtonProps, "title">(
    {
        icon: italicIcon,
        command: "italic",
    },
    {
        title: tr.editingItalicTextCtrlandi,
    }
);

const underlineButton = commandIconButton<CommandIconButtonProps, "title">(
    {
        icon: underlineIcon,
        command: "underline",
    },
    {
        title: tr.editingUnderlineTextCtrlandu,
    }
);

const superscriptButton = commandIconButton<CommandIconButtonProps, "title">(
    {
        icon: superscriptIcon,
        command: "superscript",
    },
    {
        title: tr.editingSuperscriptCtrlandand,
    }
);

const subscriptButton = commandIconButton<CommandIconButtonProps, "title">(
    {
        icon: subscriptIcon,
        command: "subscript",
    },
    {
        title: tr.editingSubscriptCtrland,
    }
);

const removeFormatButton = commandIconButton<CommandIconButtonProps, "title">(
    {
        icon: eraserIcon,
        command: "removeFormat",
        activatable: false,
    },
    {
        title: tr.editingRemoveFormattingCtrlandr,
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
