// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { dynamicComponent } from "sveltelib/dynamicComponent";

/* components */
import IconButton from "components/IconButton.svelte";
export const iconButton = dynamicComponent(IconButton as any);
