// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/* eslint
@typescript-eslint/no-explicit-any: "off",
 */

// A standalone bundle that adds mutateNextCardStates and the image occlusion API
// to the anki namespace. When all clients are using reviewer.js directly, we
// can get rid of this.

import { imageOcclusionAPI } from "$lib/../routes/image-occlusion/review";

import { mutateNextCardStates } from "./answering";
import { addBrowserClasses } from "./browser_selector";

globalThis.anki = globalThis.anki || {};
globalThis.anki.mutateNextCardStates = mutateNextCardStates;
globalThis.anki.imageOcclusion = imageOcclusionAPI;
globalThis.anki.setupImageCloze = imageOcclusionAPI.setup; // deprecated
globalThis.anki.addBrowserClasses = addBrowserClasses;
