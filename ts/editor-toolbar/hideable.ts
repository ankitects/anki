// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
interface Hideable {
    hidden?: boolean;
}

export function showComponent<T extends Hideable>(component: T): T {
    component.hidden = false;
    return component;
}

export function hideComponent<T extends Hideable>(component: T): T {
    component.hidden = true;
    return component;
}

export function toggleComponent<T extends Hideable>(component: T): T {
    component.hidden = !component.hidden;
    return component;
}
