// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
import IconButton from "editor-toolbar/IconButton.svelte";
import type { IconButtonProps } from "editor-toolbar/IconButton";
import DropdownMenu from "editor-toolbar/DropdownMenu.svelte";
import type { DropdownMenuProps } from "editor-toolbar/DropdownMenu";
import DropdownItem from "editor-toolbar/DropdownItem.svelte";
import type { DropdownItemProps } from "editor-toolbar/DropdownItem";
import WithDropdownMenu from "editor-toolbar/WithDropdownMenu.svelte";
import type { WithDropdownMenuProps } from "editor-toolbar/WithDropdownMenu";
import ButtonGroup from "editor-toolbar/ButtonGroup.svelte";
import type { ButtonGroupProps } from "editor-toolbar/ButtonGroup";

import { bridgeCommand } from "anki/bridgecommand";
import { DynamicSvelteComponent, dynamicComponent } from "sveltelib/dynamicComponent";
import * as tr from "anki/i18n";

import { wrap } from "./wrap";

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

export function getTemplateGroup(): DynamicSvelteComponent<typeof ButtonGroup> &
    ButtonGroupProps {
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

export function getTemplateMenus(): (DynamicSvelteComponent<typeof DropdownMenu> &
    DropdownMenuProps)[] {
    const mathjaxMenuItems = [
        dropdownItem({
            onClick: () => wrap("\\(", "\\)"),
            label: tr.editingMathjaxInline(),
            endLabel: "Ctrl+M, M",
        }),
        dropdownItem({
            onClick: () => wrap("\\[", "\\]"),
            label: tr.editingMathjaxBlock(),
            endLabel: "Ctrl+M, E",
        }),
        dropdownItem({
            onClick: () => wrap("\\(\\ce{", "}\\)"),
            label: tr.editingMathjaxChemistry(),
            endLabel: "Ctrl+M, C",
        }),
    ];

    const latexMenuItems = [
        dropdownItem({
            onClick: () => wrap("[latex]", "[/latex]"),
            label: tr.editingLatex(),
            endLabel: "Ctrl+T, T",
        }),
        dropdownItem({
            onClick: () => wrap("[$]", "[/$]"),
            label: tr.editingLatexEquation(),
            endLabel: "Ctrl+T, E",
        }),
        dropdownItem({
            onClick: () => wrap("[$$]", "[/$$]"),
            label: tr.editingLatexMathEnv(),
            endLabel: "Ctrl+T, M",
        }),
    ];

    const mathjaxMenu = dropdownMenu({
        id: mathjaxMenuId,
        menuItems: [...mathjaxMenuItems, ...latexMenuItems],
    });

    return [mathjaxMenu];
}
