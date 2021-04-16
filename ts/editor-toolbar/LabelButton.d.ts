// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
export interface LabelButtonProps {
    id?: string;
    className?: string;

    label: string;
    tooltip: string;
    onClick: (event: MouseEvent) => void;
    disables?: boolean;
}
