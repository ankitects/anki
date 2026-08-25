// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

export interface ContextMenuMouseEvent {
    clientX: number;
    clientY: number;
    preventDefault(): void;
}

export interface ContextMenuAPI {
    show(event: ContextMenuMouseEvent): void;
}
