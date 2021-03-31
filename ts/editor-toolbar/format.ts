import CommandIconButton from "./CommandIconButton.svelte";

import { withLazyProperties } from "anki/lazy";
import * as tr from "anki/i18n";

import boldIcon from "./type-bold.svg";
import italicIcon from "./type-italic.svg";
import underlineIcon from "./type-underline.svg";
import superscriptIcon from "./format-superscript.svg";
import subscriptIcon from "./format-subscript.svg";
import eraserIcon from "./eraser.svg";

const boldButton = withLazyProperties(
    {
        component: CommandIconButton,
        icon: boldIcon,
        command: "bold",
    },
    {
        title: tr.editingBoldTextCtrlandb,
    }
);

const italicButton = withLazyProperties(
    {
        component: CommandIconButton,
        icon: italicIcon,
        command: "italic",
    },
    {
        title: tr.editingItalicTextCtrlandi,
    }
);

const underlineButton = withLazyProperties(
    {
        component: CommandIconButton,
        icon: underlineIcon,
        command: "underline",
    },
    {
        title: tr.editingUnderlineTextCtrlandu,
    }
);

const superscriptButton = withLazyProperties(
    {
        component: CommandIconButton,
        icon: superscriptIcon,
        command: "superscript",
    },
    {
        title: tr.editingSuperscriptCtrlandand,
    }
);

const subscriptButton = withLazyProperties(
    {
        component: CommandIconButton,
        icon: subscriptIcon,
        command: "subscript",
    },
    {
        title: tr.editingSubscriptCtrland,
    }
);

const removeFormatButton = withLazyProperties(
    {
        component: CommandIconButton,
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
