// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
import type { ToolbarItem } from "./types";

export interface ButtonGroupProps {
    id: string;
    className?: string;
    buttons: ToolbarItem[];
}
