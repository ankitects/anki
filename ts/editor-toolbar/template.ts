import IconButton from "./IconButton.svelte";
import type { IconButtonProps } from "./IconButton";
import DropdownMenu from "./DropdownMenu.svelte";
import type { DropdownMenuProps } from "./DropdownMenu";
import DropdownItem from "./DropdownItem.svelte";
import type { DropdownItemProps } from "./DropdownItem";
import WithDropdownMenu from "./WithDropdownMenu.svelte";
import type { WithDropdownMenuProps } from "./WithDropdownMenu";

import { bridgeCommand } from "anki/bridgecommand";
import { dynamicComponent } from "sveltelib/dynamicComponent";
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

const iconButton = dynamicComponent(IconButton);

const withDropdownMenu = dynamicComponent(WithDropdownMenu);
const dropdownMenu = dynamicComponent(DropdownMenu);
const dropdownItem = dynamicComponent(DropdownItem);

const attachmentButton = iconButton<IconButtonProps, "tooltip">(
    {
        icon: paperclipIcon,
        onClick: onAttachment,
    },
    {
        tooltip: tr.editingAttachPicturesaudiovideoF3,
    }
);

const recordButton = iconButton(
    { icon: micIcon, onClick: onRecord },
    {
        tooltip: tr.editingRecordAudioF5,
    }
);

const clozeButton = iconButton<IconButtonProps, "tooltip">(
    {
        icon: bracketsIcon,
        onClick: onCloze,
    },
    {
        tooltip: tr.editingClozeDeletionCtrlandshiftandc,
    }
);

const mathjaxButton = iconButton<Omit<IconButtonProps, "onClick" | "tooltip">>(
    {
        icon: functionIcon,
    },
    {}
);

const mathjaxMenuId = "mathjaxMenu";

const mathjaxMenu = dropdownMenu<DropdownMenuProps>(
    {
        id: mathjaxMenuId,
        menuItems: [
            dropdownItem<DropdownItemProps, "label">(
                {
                    onClick: () => bridgeCommand("mathjaxInline"),
                    tooltip: "test",
                    endLabel: "test",
                },
                { label: tr.editingMathjaxInline }
            ),
            dropdownItem<DropdownItemProps, "label">(
                {
                    onClick: () => bridgeCommand("mathjaxBlock"),
                    tooltip: "test",
                    endLabel: "test",
                },
                { label: tr.editingMathjaxBlock }
            ),
            dropdownItem<DropdownItemProps, "label">(
                {
                    onClick: () => bridgeCommand("mathjaxChemistry"),
                    tooltip: "test",
                    endLabel: "test",
                },
                { label: tr.editingMathjaxChemistry }
            ),
        ],
    },
    {}
);

const mathjaxButtonWithMenu = withDropdownMenu<WithDropdownMenuProps>(
    {
        button: mathjaxButton,
        menuId: mathjaxMenuId,
    },
    {}
);

const htmlButton = iconButton<IconButtonProps, "tooltip">(
    {
        icon: xmlIcon,
        onClick: onHtmlEdit,
    },
    {
        tooltip: tr.editingHtmlEditor,
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
