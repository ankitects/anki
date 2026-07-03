// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { truthy } from "@tslib/functional";

interface ActionReturn<P> {
    destroy?(): void;
    update?(params: P): void;
}

type Action<E extends HTMLElement, P> = (
    element: E,
    params: P,
) => ActionReturn<P> | void;

/**
 * A helper function for treating a list of Svelte actions as a single Svelte action
 * and use it with a single `use:` directive
 */
function actionList<E extends HTMLElement, P>(actions: Action<E, P>[]): Action<E, P> {
    return function action(element: E, params: P): ActionReturn<P> | void {
        const results = actions.map((action) => action(element, params)).filter(truthy);

        return {
            update(params: P) {
                for (const { update } of results) {
                    update?.(params);
                }
            },
            destroy() {
                for (const { destroy } of results) {
                    destroy?.();
                }
            },
        };
    };
}

export default actionList;
