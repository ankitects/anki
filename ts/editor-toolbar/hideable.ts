// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
interface Hideable {
    hidden?: boolean;
}

export function showComponent(component: Hideable): void {
    component.hidden = false;
}

export function hideComponent(component: Hideable): void {
    component.hidden = true;
}

export function toggleComponent(component: Hideable): void {
    component.hidden = !component.hidden;
}
