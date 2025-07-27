// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { mount, unmount } from "svelte";
import Toast from "./Toast.svelte";
import type { ToastProps } from "./types";

const toastProps: ToastProps = $state({
    showToast: false,
    type: "success",
    message: "",
});

export function initToast(): Toast {
    return mount(Toast, {
        target: document.body,
        props: toastProps,
    });
}

export function destroyToast(toast: Toast) {
    unmount(toast);
}

export function showToast(message: string, type: "success" | "error") {
    toastProps.message = message;
    toastProps.type = type;
    toastProps.showToast = true;
}

export function hideToast() {
    toastProps.showToast = false;
}
