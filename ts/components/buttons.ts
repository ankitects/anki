// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
import type { Writable } from "svelte/store";
import type { Registration } from "./registration";

export enum ButtonPosition {
    Standalone,
    Leftmost,
    Center,
    Rightmost,
}

export interface ButtonRegistration extends Registration {
    position: Writable<ButtonPosition>;
}
