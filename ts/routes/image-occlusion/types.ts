// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

export interface Size {
    width: number;
    height: number;
}

export type ConstructorParams<T> = {
    [P in keyof T]?: T[P];
};

export interface ToastProps {
    message: string;
    type: "success" | "error";
    showToast: boolean;
}
