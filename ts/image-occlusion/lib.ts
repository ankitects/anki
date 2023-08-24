// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

export interface IOAddingMode {
    kind: "add";
    notetypeId: number;
    imagePath?: string;
    imageFieldHtml?: string;
}

export interface IOEditingMode {
    kind: "edit";
    noteId: number;
}

export type IOMode = IOAddingMode | IOEditingMode;
