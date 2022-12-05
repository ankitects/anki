// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/**
 * @param element: The element to be moved.
 * @param targetElement DOM Element where element is going to be appended
 */
function portal(
    element: HTMLElement,
    targetElement: Element | null = document.body,
): { update(target: Element): void; destroy(): void } {
    let target: Element | null;

    async function update(newTarget: Element | null) {
        target = newTarget;

        if (!target) {
            return;
        }

        target.append(element);
    }

    function destroy() {
        element.remove();
    }

    update(targetElement);

    return {
        update,
        destroy,
    };
}

export default portal;
