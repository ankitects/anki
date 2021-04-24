// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
export interface Option {
    label: string;
    value: string;
    selected: boolean;
}

export interface SelectButtonProps {
    id: string;
    className?: string;
    tooltip?: string;
    disables: boolean;
    options: Option[];
}
