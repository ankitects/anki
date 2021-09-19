// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

export interface FieldData {
    fieldName: string;
    fieldContent: string;
    fontName: string;
    fontSize: number;
    rtl: boolean;
    sticky: boolean | null;
}

export interface AdapterData {
    fieldsData: FieldData[];
    tags: string[];
    textColor: string;
    highlightColor: string;
    focusTo: number;
}
