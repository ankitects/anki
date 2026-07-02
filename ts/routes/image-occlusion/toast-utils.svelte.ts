// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { mount, unmount } from "svelte";
import Toast from "./Toast.svelte";
import type { ToastProps } from "./types";

const toastProps: ToastProps = $state({
    type: "success",
    message: "",
});

let toast: Toast | null = null;

export function initToast() {
    toast = mount(Toast, {
        target: document.body,
        props: toastProps,
    });
}

export function destroyToast() {
    if (toast) {
        unmount(toast);
        toast = null;
    }
}

export function showToast(message: string, type: "success" | "error", timeout?: number) {
    toastProps.message = message;
    toastProps.type = type;
    toastProps.timeout = timeout;
    toast?.show();
}

export function hideToast() {
    toast?.hide();
}
