// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
export interface DropdownItemProps {
    id?: string;
    className?: string;
    tooltip: string;

    onClick: (event: MouseEvent) => void;
    label: string;
    endLabel: string;
}
