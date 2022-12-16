// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

export type EditorOptions = {
    fieldsCollapsed: boolean[];
    fieldStates: {
        richTextsHidden: boolean[];
        plainTextsHidden: boolean[];
        plainTextDefaults: boolean[];
    };
};

export type SessionOptions = {
    [key: number]: EditorOptions;
};
