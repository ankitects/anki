// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
import type { ToolbarItem } from "sveltelib/types";

export interface WithLabelProps {
    id?: string;
    className?: string;

    button: ToolbarItem;
    label: string;
}
