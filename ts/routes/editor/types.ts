// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

export type EditorOptions = {
    fieldsCollapsed: boolean[];
    fieldStates: {
        richTextsHidden: boolean[];
        plainTextsHidden: boolean[];
        plainTextDefaults: boolean[];
    };
    modTimeOfNotetype: bigint;
};

export type SessionOptions = {
    [key: string]: EditorOptions;
};

export type NotetypeIdAndModTime = {
    id: bigint;
    modTime: bigint;
};

export enum EditorState {
    Initial = -1,
    Fields = 0,
    ImageOcclusionPicker = 1,
    ImageOcclusionMasks = 2,
    ImageOcclusionFields = 3,
}

export type EditorMode = "add" | "browser" | "current";
