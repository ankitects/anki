// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/**
 * @param element: The element to be moved.
 * @param target DOM Element where element is going to be appended
 */
function portal(
    element: HTMLElement,
    targetElement: Element = document.body,
): { update(target: Element): void; destroy(): void } {
    let target: Element = targetElement;

    async function update(newTarget: Element) {
        target = newTarget;
        target.append(element);
    }

    function destroy() {
        element.remove();
    }

    update(target);

    return {
        update,
        destroy,
    };
}

export default portal;
