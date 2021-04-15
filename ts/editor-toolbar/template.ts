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
import { DynamicSvelteComponent, dynamicComponent } from "sveltelib/dynamicComponent";
import * as tr from "anki/i18n";

import paperclipIcon from "./paperclip.svg";
import micIcon from "./mic.svg";
import functionIcon from "./function-variant.svg";
import xmlIcon from "./xml.svg";

import { getClozeButton } from "./cloze";

function onAttachment(): void {
    bridgeCommand("attach");
}

function onRecord(): void {
    bridgeCommand("record");
}

function onHtmlEdit(): void {
    bridgeCommand("htmlEdit");
}

const mathjaxMenuId = "mathjaxMenu";

const iconButton = dynamicComponent<typeof IconButton, IconButtonProps>(IconButton);
const withDropdownMenu = dynamicComponent<
    typeof WithDropdownMenu,
    WithDropdownMenuProps
>(WithDropdownMenu);
const dropdownMenu = dynamicComponent<typeof DropdownMenu, DropdownMenuProps>(
    DropdownMenu
);
const dropdownItem = dynamicComponent<typeof DropdownItem, DropdownItemProps>(
    DropdownItem
);
const buttonGroup = dynamicComponent<typeof ButtonGroup, ButtonGroupProps>(ButtonGroup);

export function getTemplateGroup(): DynamicSvelteComponent<typeof ButtonGroup> & ButtonGroupProps {
    const attachmentButton = iconButton({
        icon: paperclipIcon,
        onClick: onAttachment,
        tooltip: tr.editingAttachPicturesaudiovideoF3(),
    });

    const recordButton = iconButton({
        icon: micIcon,
        onClick: onRecord,
        tooltip: tr.editingRecordAudioF5(),
    });

    const mathjaxButton = iconButton({
        icon: functionIcon,
        foo: 5,
    });

    const mathjaxButtonWithMenu = withDropdownMenu({
        button: mathjaxButton,
        menuId: mathjaxMenuId,
    });

    const htmlButton = iconButton({
        icon: xmlIcon,
        onClick: onHtmlEdit,
        tooltip: tr.editingHtmlEditor,
    });

    return buttonGroup({
        id: "template",
        buttons: [
            attachmentButton,
            recordButton,
            getClozeButton(),
            mathjaxButtonWithMenu,
            htmlButton,
        ],
    });
}

export function getTemplateMenus(): (DynamicSvelteComponent<typeof DropdownMenu> & DropdownMenuProps)[] {
    const mathjaxMenu = dropdownMenu({
        id: mathjaxMenuId,
        menuItems: [
            dropdownItem({
                // @ts-expect-error
                onClick: () => wrap("\\(", "\\)"),
                tooltip: "test",
                endLabel: "test",
                label: tr.editingMathjaxInline(),
            }),
            dropdownItem({
                // @ts-expect-error
                onClick: () => wrap("\\[", "\\]"),
                tooltip: "test",
                endLabel: "test",
                label: tr.editingMathjaxBlock(),
            }),
            dropdownItem({
                // @ts-expect-error
                onClick: () => wrap("\\(\\ce{", "}\\)"),
                tooltip: "test",
                endLabel: "test",
                label: tr.editingMathjaxChemistry(),
            }),
        ],
    });

    return [mathjaxMenu];
}
