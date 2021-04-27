// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
import type { ToolbarItem } from "./types";

export interface ButtonDropdownProps {
    id: string;
    className?: string;
    items: ToolbarItem[];
}
