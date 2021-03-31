import CommandIconButton from "./CommandIconButton.svelte";

import { lazyProperties } from "anki/lazy";
import * as tr from "anki/i18n";

import boldIcon from "./type-bold.svg";
import italicIcon from "./type-italic.svg";
import underlineIcon from "./type-underline.svg";
import superscriptIcon from "./format-superscript.svg";
import subscriptIcon from "./format-subscript.svg";
import eraserIcon from "./eraser.svg";

const boldButton = {
    component: CommandIconButton,
    icon: boldIcon,
    command: "bold",
};

lazyProperties(boldButton, {
    title: tr.editingBoldTextCtrlandb,
});

const italicButton = {
    component: CommandIconButton,
    icon: italicIcon,
    command: "italic",
};

lazyProperties(italicButton, {
    title: tr.editingItalicTextCtrlandi,
});

const underlineButton = {
    component: CommandIconButton,
    icon: underlineIcon,
    command: "underline",
};

lazyProperties(underlineButton, {
    title: tr.editingUnderlineTextCtrlandu,
});

const superscriptButton = {
    component: CommandIconButton,
    icon: superscriptIcon,
    command: "superscript",
};

lazyProperties(superscriptButton, {
    title: tr.editingSuperscriptCtrlandand,
});

const subscriptButton = {
    component: CommandIconButton,
    icon: subscriptIcon,
    command: "subscript",
};

lazyProperties(subscriptButton, {
    title: tr.editingSubscriptCtrland,
});

const removeFormatButton = {
    component: CommandIconButton,
    icon: eraserIcon,
    command: "removeFormat",
    activatable: false,
};

lazyProperties(removeFormatButton, {
    title: tr.editingRemoveFormattingCtrlandr,
});

export const formatButtons = [
    boldButton,
    italicButton,
    underlineButton,
    superscriptButton,
    subscriptButton,
    removeFormatButton,
];
