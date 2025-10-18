// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import * as _tr from "@generated/ftl-launcher";
import type { GetLangsResponse_Pair, GetMirrorsResponse_Pair, GetVersionsResponse } from "@generated/anki/launcher_pb";
import { writable } from "svelte/store";

export const zoomFactor = writable(1.2);
export const langsStore = writable<GetLangsResponse_Pair[]>([]);
export const mirrorsStore = writable<GetMirrorsResponse_Pair[]>([]);
export const currentLang = writable("");
export const initialLang = writable("");
export const versionsStore = writable<GetVersionsResponse | undefined>(undefined);

export const tr = writable(_tr);
currentLang.subscribe(() => tr.set(_tr));
