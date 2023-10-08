// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

export type EditorOptions = {
    fieldsCollapsed: boolean[];
    fieldStates: {
        richTextsHidden: boolean[];
        plainTextsHidden: boolean[];
        plainTextDefaults: boolean[];
    };
    modTimeOfNotetype: number;
};

export type SessionOptions = {
    [key: number]: EditorOptions;
};

export type NotetypeIdAndModTime = {
    id: number;
    modTime: number;
};

export enum EditorState {
    Initial = -1,
    Fields = 0,
    ImageOcclusionPicker = 1,
    ImageOcclusionMasks = 2,
    ImageOcclusionFields = 3,
}
