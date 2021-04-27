// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
export interface CommandIconButtonProps {
    id?: string;
    className?: string;
    tooltip: string;
    icon: string;

    command: string;
    onClick: (event: MouseEvent) => void;

    onUpdate: (event: Event) => boolean;

    disables?: boolean;
    dropdownToggle?: boolean;
}
