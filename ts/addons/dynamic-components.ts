// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { dynamicComponent } from "sveltelib/dynamicComponent";

/* event handlers */
import OnClick from "./OnClick.svelte";
export const onClick = dynamicComponent(OnClick as any);

import OnMount from "./OnMount.svelte";
export const onMount = dynamicComponent(OnMount as any);

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
