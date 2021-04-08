import IconButton from "./IconButton.svelte";
import type { IconButtonProps } from "./IconButton";
import DropdownMenu from "./DropdownMenu.svelte";
import type { DropdownMenuProps } from "./DropdownMenu";
import DropdownItem from "./DropdownItem.svelte";
import type { DropdownItemProps } from "./DropdownItem";
import WithDropdownMenu from "./WithDropdownMenu.svelte";
import type { WithDropdownMenuProps } from "./WithDropdownMenu";
import ButtonGroup from "./ButtonGroup.svelte";
import type { ButtonGroupProps } from "./ButtonGroup";

import { bridgeCommand } from "anki/bridgecommand";
import { dynamicComponent } from "sveltelib/dynamicComponent";
import * as tr from "anki/i18n";

import paperclipIcon from "./paperclip.svg";
import micIcon from "./mic.svg";
import functionIcon from "./function-variant.svg";
import xmlIcon from "./xml.svg";

import { clozeButton } from "./cloze";

function onAttachment(): void {
    bridgeCommand("attach");
}

function onRecord(): void {
    bridgeCommand("record");
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
                    // @ts-expect-error
                    onClick: () => wrap("\\(", "\\)"),
                    tooltip: "test",
                    endLabel: "test",
                },
                { label: tr.editingMathjaxInline }
            ),
            dropdownItem<DropdownItemProps, "label">(
                {
                    // @ts-expect-error
                    onClick: () => wrap("\\[", "\\]"),
                    tooltip: "test",
                    endLabel: "test",
                },
                { label: tr.editingMathjaxBlock }
            ),
            dropdownItem<DropdownItemProps, "label">(
                {
                    // @ts-expect-error
                    onClick: () => wrap("\\(\\ce{", "}\\)"),
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

const buttonGroup = dynamicComponent(ButtonGroup);
export const templateGroup = buttonGroup<ButtonGroupProps>(
    {
        id: "template",
        buttons: [
            attachmentButton,
            recordButton,
            clozeButton,
            mathjaxButtonWithMenu,
            htmlButton,
        ],
    },
    {}
);

export const templateMenus = [mathjaxMenu];
