// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/* eslint
@typescript-eslint/no-explicit-any: "off",
 */

import type { Identifier } from "@tslib/children-access";
import type { ChildrenAccess } from "@tslib/children-access";
import childrenAccess from "@tslib/children-access";
import { nodeIsElement } from "@tslib/dom";
import type { Callback } from "@tslib/helpers";
import { removeItem } from "@tslib/helpers";
import { promiseWithResolver } from "@tslib/promise";
import type { SvelteComponent } from "svelte";
import type { Readable, Writable } from "svelte/store";
import { writable } from "svelte/store";

export interface DynamicSvelteComponent {
    component: typeof SvelteComponent<any>;
    /**
     * Props that are passed to the component
     */
    props?: Record<string, unknown>;
    /**
     * ID that will be assigned to the component that hosts
     * the dynamic component (slot host)
     */
    id?: string;
}

/**
 * Props that will be passed to the slot host, e.g. ButtonGroupItem.
 */
export interface SlotHostProps {
    detach: Writable<boolean>;
}

export interface CreateInterfaceAPI<T extends SlotHostProps, U extends Element> {
    addComponent(
        component: DynamicSvelteComponent,
        reinsert: (newElement: U, access: ChildrenAccess<U>) => number,
    ): Promise<{ destroy: Callback }>;
    updateProps(update: (hostProps: T) => T, identifier: Identifier): Promise<boolean>;
}

export interface GetSlotHostProps<T> {
    getProps(): T;
}

export interface DynamicSlotted<T extends SlotHostProps = SlotHostProps> {
    component: DynamicSvelteComponent;
    hostProps: T;
}

export interface DynamicSlottingAPI<
    T extends SlotHostProps,
    U extends Element,
    X extends Record<string, unknown>,
> {
    /**
     * This should be used as an action on the element that hosts the slot hosts.
     */
    resolveSlotContainer: (element: U) => void;
    /**
     * Contains the props for the DynamicSlot component
     */
    dynamicSlotted: Readable<DynamicSlotted<T>[]>;
    slotsInterface: X;
}

/**
 * Allow add-on developers to dynamically extend/modify components our components
 *
 * @remarks
 * It allows to insert elements in between the components, or modify their props.
 * Practically speaking, we let Svelte do the initial insertion of an element,
 * but then immediately move it to its destination, and save a reference to it.
 *
 * @experimental
 */
function dynamicSlotting<
    T extends SlotHostProps,
    U extends Element,
    X extends Record<string, unknown>,
>(
    /**
     * A function which will create props which are passed to the dynamically
     * slotted component's host component, the slot host, e.g. `ButtonGroupItem`
     */
    makeProps: () => T,
    /**
     * This is called on *all* items whenever any item updates
     */
    updatePropsList: (propsList: T[]) => T[],
    /**
     * A function to create an interface to interact with slotted components
     */
    setSlotHostContext: (callback: GetSlotHostProps<T>) => void,
    createInterface: (api: CreateInterfaceAPI<T, U>) => X,
): DynamicSlottingAPI<T, U, X> {
    const slotted = writable<T[]>([]);
    slotted.subscribe(updatePropsList);

    function addDynamicallySlotted(index: number, props: T): void {
        slotted.update((slotted: T[]): T[] => {
            slotted.splice(index, 0, props);
            return slotted;
        });
    }

    const [elementPromise, resolveSlotContainer] = promiseWithResolver<U>();
    const accessPromise = elementPromise.then(childrenAccess);

    const dynamicSlotted = writable<DynamicSlotted<T>[]>([]);

    async function addComponent(
        component: DynamicSvelteComponent,
        reinsert: (newElement: U, access: ChildrenAccess<U>) => number,
    ): Promise<{ destroy: Callback }> {
        const [dynamicallySlottedMounted, resolveDynamicallySlotted] = promiseWithResolver();
        const access = await accessPromise;
        const hostProps = makeProps();

        function elementIsDynamicComponent(element: Element): boolean {
            return !component.id || element.id === component.id;
        }

        async function callback(
            mutations: MutationRecord[],
            observer: MutationObserver,
        ): Promise<void> {
            for (const mutation of mutations) {
                for (const addedNode of mutation.addedNodes) {
                    if (
                        !nodeIsElement(addedNode)
                        || !elementIsDynamicComponent(addedNode)
                    ) {
                        continue;
                    }

                    const theElement = addedNode as U;
                    const index = reinsert(theElement, access);

                    if (index >= 0) {
                        addDynamicallySlotted(index, hostProps);
                    }

                    resolveDynamicallySlotted(undefined);
                    return observer.disconnect();
                }
            }
        }

        const observer = new MutationObserver(callback);
        observer.observe(access.parent, { childList: true });

        const dynamicSlot = {
            component,
            hostProps,
        };

        dynamicSlotted.update(
            (dynamicSlotted: DynamicSlotted<T>[]): DynamicSlotted<T>[] => {
                dynamicSlotted.push(dynamicSlot);
                return dynamicSlotted;
            },
        );

        await dynamicallySlottedMounted;

        return {
            destroy() {
                dynamicSlotted.update(
                    (dynamicSlotted: DynamicSlotted<T>[]): DynamicSlotted<T>[] => {
                        // TODO needs testing, if Svelte actually correctly removes the element
                        removeItem(dynamicSlotted, dynamicSlot);
                        return dynamicSlotted;
                    },
                );
            },
        };
    }

    async function updateProps(
        update: (props: T) => T,
        identifier: Identifier,
    ): Promise<boolean> {
        const access = await accessPromise;

        return access.updateElement((_element: U, index: number): void => {
            slotted.update((slottedProps: T[]) => {
                slottedProps[index] = update(slottedProps[index]);
                return slottedProps;
            });
        }, identifier);
    }

    const slotsInterface = createInterface({ addComponent, updateProps });

    function getSlotHostProps(): T {
        const props = makeProps();

        slotted.update((slotted: T[]): T[] => {
            slotted.push(props);
            return slotted;
        });

        return props;
    }

    setSlotHostContext({ getProps: getSlotHostProps });

    return {
        dynamicSlotted,
        resolveSlotContainer,
        slotsInterface,
    };
}

export default dynamicSlotting;

/** Convenient default functions for dynamic slotting */

export function defaultProps(): SlotHostProps {
    return {
        detach: writable(false),
    };
}

export interface DefaultSlotInterface extends Record<string, unknown> {
    insert(
        button: DynamicSvelteComponent,
        position?: Identifier,
    ): Promise<{ destroy: Callback }>;
    append(
        button: DynamicSvelteComponent,
        position?: Identifier,
    ): Promise<{ destroy: Callback }>;
    show(position: Identifier): Promise<boolean>;
    hide(position: Identifier): Promise<boolean>;
    toggle(position: Identifier): Promise<boolean>;
    setShown(position: Identifier, shown: boolean): Promise<boolean>;
}

export function defaultInterface<T extends SlotHostProps, U extends Element>({
    addComponent,
    updateProps,
}: CreateInterfaceAPI<T, U>): DefaultSlotInterface {
    function insert(
        component: DynamicSvelteComponent,
        id: Identifier = 0,
    ): Promise<{ destroy: Callback }> {
        return addComponent(
            component,
            (element: Element, access: ChildrenAccess<U>) => access.insertElement(element, id),
        );
    }

    function append(
        component: DynamicSvelteComponent,
        id: Identifier = -1,
    ): Promise<{ destroy: Callback }> {
        return addComponent(
            component,
            (element: Element, access: ChildrenAccess<U>) => access.appendElement(element, id),
        );
    }

    function show(id: Identifier): Promise<boolean> {
        return updateProps((props: T): T => {
            props.detach.set(false);
            return props;
        }, id);
    }

    function hide(id: Identifier): Promise<boolean> {
        return updateProps((props: T): T => {
            props.detach.set(true);
            return props;
        }, id);
    }

    function toggle(id: Identifier): Promise<boolean> {
        return updateProps((props: T): T => {
            props.detach.update((detached: boolean) => !detached);
            return props;
        }, id);
    }

    function setShown(id: Identifier, shown: boolean): Promise<boolean> {
        return updateProps((props: T): T => {
            props.detach.set(!shown);
            return props;
        }, id);
    }

    return {
        insert,
        append,
        show,
        hide,
        toggle,
        setShown,
    };
}

import contextProperty from "./context-property";

const key = Symbol("dynamicSlotting");
const [defaultSlotHostContext, setSlotHostContext] = contextProperty<GetSlotHostProps<SlotHostProps>>(key);

export { defaultSlotHostContext, setSlotHostContext };
