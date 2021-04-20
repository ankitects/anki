// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
import type DropdownMenu from "editor-toolbar/DropdownMenu.svelte";
import type { DropdownMenuProps } from "editor-toolbar/DropdownMenu";
import type ButtonGroup from "editor-toolbar/ButtonGroup.svelte";
import type { ButtonGroupProps } from "editor-toolbar/ButtonGroup";
import type { DynamicSvelteComponent } from "sveltelib/dynamicComponent";

import { bridgeCommand } from "anki/bridgecommand";
import {
    iconButton,
    withDropdownMenu,
    dropdownMenu,
    dropdownItem,
    buttonGroup,
} from "editor-toolbar/dynamicComponents";
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
