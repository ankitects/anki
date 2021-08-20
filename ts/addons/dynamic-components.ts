// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { dynamicComponent } from "sveltelib/dynamicComponent";

/* components */
import IconButton from "./IconButton.svelte";
export const iconButton = dynamicComponent(IconButton as any);

/* decorators */
import WithContext from "./WithContext.svelte";
export const withContext = dynamicComponent(WithContext as any);

import WithState from "./WithState.svelte";
export const withState = dynamicComponent(WithState as any);

import WithShortcut from "./WithShortcut.svelte";
export const withShortcut = dynamicComponent(WithShortcut as any);

/* slots */
import Slotted from "./Slotted.svelte";
export const slotted = dynamicComponent(Slotted as any);

import SlottedHTML from "./SlottedHTML.svelte";
export const slottedHtml = dynamicComponent(SlottedHTML as any);
