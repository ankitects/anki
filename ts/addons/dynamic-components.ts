// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { dynamicComponent } from "sveltelib/dynamicComponent";

/* components */
import IconButton from "./IconButton.svelte";
export const iconButton = dynamicComponent(IconButton);

/* decorators */
import WithContext from "./WithContext.svelte";
export const withContext = dynamicComponent(WithContext);

import WithState from "./WithState.svelte";
export const withState = dynamicComponent(WithState);

import WithShortcut from "./WithShortcut.svelte";
export const withShortcut = dynamicComponent(WithShortcut);

/* slots */
import Slotted from "./Slotted.svelte";
export const slotted = dynamicComponent(Slotted);

import SlottedHTML from "./SlottedHTML.svelte";
export const slottedHtml = dynamicComponent(SlottedHTML);
