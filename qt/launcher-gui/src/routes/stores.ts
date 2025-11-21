// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import * as _tr from "@generated/ftl-launcher";
import { writable } from "svelte/store";

export const zoomFactor = writable(1.2);
export const tr = writable(_tr);
