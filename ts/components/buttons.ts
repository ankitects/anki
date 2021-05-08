// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
import type { Writable } from "svelte/store";

export enum ButtonPosition {
    Standalone,
    Leftmost,
    Center,
    Rightmost,
}

export interface ButtonRegistration {
    detach: Writable<boolean>;
    position: Writable<ButtonPosition>;
}

export interface ButtonGroupRegistration {
    detach: Writable<boolean>;
}
