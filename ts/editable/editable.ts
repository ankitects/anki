// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

const customInput = new Event("custominput", { bubbles: true });

export function signifyCustomInput(element: Element): void {
    element.dispatchEvent(customInput);
}
