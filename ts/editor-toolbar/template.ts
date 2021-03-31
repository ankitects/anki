import IconButton from "./IconButton.svelte";
import DropdownMenu from "./DropdownMenu.svelte";
import DropdownItem from "./DropdownItem.svelte";
import WithDropdownMenu from "./WithDropdownMenu.svelte";

import { bridgeCommand } from "anki/bridgecommand";
import { withLazyProperties } from "anki/lazy";
import * as tr from "anki/i18n";

import paperclipIcon from "./paperclip.svg";
import micIcon from "./mic.svg";
import bracketsIcon from "./code-brackets.svg";
import functionIcon from "./function-variant.svg";
import xmlIcon from "./xml.svg";

function onAttachment(): void {
    bridgeCommand("attach");
}

function onRecord(): void {
    bridgeCommand("record");
}

function onCloze(): void {
    bridgeCommand("cloze");
}

function onHtmlEdit(): void {
    bridgeCommand("htmlEdit");
}

const attachmentButton = withLazyProperties(
    {
        component: IconButton,
        icon: paperclipIcon,
        onClick: onAttachment,
    },
    {
        title: tr.editingAttachPicturesaudiovideoF3,
    }
);

const recordButton = withLazyProperties(
    { component: IconButton, icon: micIcon, onClick: onRecord },
    {
        title: tr.editingRecordAudioF5,
    }
);

const clozeButton = withLazyProperties(
    {
        component: IconButton,
        icon: bracketsIcon,
        onClick: onCloze,
    },
    {
        title: tr.editingClozeDeletionCtrlandshiftandc,
    }
);

const mathjaxButton = {
    component: IconButton,
    icon: functionIcon,
};

const mathjaxMenuId = "mathjaxMenu";

const mathjaxMenu = {
    component: DropdownMenu,
    id: mathjaxMenuId,
    menuItems: [
        withLazyProperties(
            { component: DropdownItem, onClick: () => bridgeCommand("mathjaxInline") },
            { label: tr.editingMathjaxInline }
        ),
        withLazyProperties(
            { component: DropdownItem, onClick: () => bridgeCommand("mathjaxBlock") },
            { label: tr.editingMathjaxBlock }
        ),
        withLazyProperties(
            {
                component: DropdownItem,
                onClick: () => bridgeCommand("mathjaxChemistry"),
            },
            { label: tr.editingMathjaxChemistry }
        ),
    ],
};

const mathjaxButtonWithMenu = {
    component: WithDropdownMenu,
    button: mathjaxButton,
    menuId: mathjaxMenuId,
};

const htmlButton = withLazyProperties(
    {
        component: IconButton,
        icon: xmlIcon,
        onClick: onHtmlEdit,
    },
    {
        title: tr.editingHtmlEditor,
    }
);

export const templateButtons = [
    attachmentButton,
    recordButton,
    clozeButton,
    mathjaxButtonWithMenu,
    htmlButton,
];

export const templateMenus = [mathjaxMenu];
