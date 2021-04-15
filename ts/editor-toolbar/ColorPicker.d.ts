// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
export interface ColorPickerProps {
    id?: string;
    className?: string;
    tooltip: string;
    onChange: (event: Event) => void;
}
