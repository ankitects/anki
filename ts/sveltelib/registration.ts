// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
import type { SvelteComponent } from "svelte";
import type { Writable, Readable } from "svelte/store";
import { writable } from "svelte/store";
import type { Identifier } from "../lib/children-access";
import { promiseWithResolver } from "../lib/promise";
import type { ChildrenAccess } from "../lib/children-access";
import childrenAccess from "../lib/children-access";
import { nodeIsElement } from "../lib/dom";

/**
 * Props will be passed to the component, the id will be passed
 * to the component that hosts the dynamic component.
 */
export interface DynamicSvelteComponent {
    component: typeof SvelteComponent;
    props?: Record<string, unknown>;
    id?: string;
}

/**
 * The registration is the object that will be passed to the
 * component that hosts the dynamic component.
 */
export interface Registration {
    detach: Writable<boolean>;
}

/**
 * A function that creates a new registration object.
 */
export type Register<T extends Registration> = (index?: number, registration?: T) => T;

export interface CreateInterfaceAPI<T extends Registration, U extends Element> {
    addComponent(
        component: DynamicSvelteComponent,
        reinsert: (newElement: U, access: ChildrenAccess<U>) => number,
    ): void;
    updateRegistration(update: (registration: T) => void, identifier: Identifier): void;
}

export type CreateInterface<T extends Registration, U extends Element> = (
    callback?: (api: CreateInterfaceAPI<T, U>) => void,
) => void;

export interface RegistrationAPI<T extends Registration, U extends Element> {
    registerComponent: Register<T>;
    createInterface: CreateInterface<T, U>;
    resolve: (element: U) => void;
    /** Contains registrations for _all_ components */
    items: Readable<T[]>;
    /** Contains registrations for dynamic components only */
    dynamicItems: Readable<[DynamicSvelteComponent, T][]>;
}

function defaultInterface<T extends Registration, U extends Element>({
    addComponent,
    updateRegistration,
}: CreateInterfaceAPI<T, U>): unknown {
    function insert(component: DynamicSvelteComponent, id: Identifier = 0): void {
        addComponent(component, (element: Element, access: ChildrenAccess<U>) =>
            access.insertElement(element, id),
        );
    }

    function append(component: DynamicSvelteComponent, id: Identifier = -1): void {
        addComponent(component, (element: Element, access: ChildrenAccess<U>) =>
            access.appendElement(element, id),
        );
    }

    function show(id: Identifier): void {
        updateRegistration(({ detach }) => detach.set(false), id);
    }

    function hide(id: Identifier): void {
        updateRegistration(({ detach }) => detach.set(true), id);
    }

    function toggle(id: Identifier): void {
        updateRegistration(
            ({ detach }) => detach.update((value: boolean): boolean => !value),
            id,
        );
    }

    return {
        insert,
        append,
        show,
        hide,
        toggle,
    };
}

/**
 * This API allows add-on developers to dynamically access components inserted by Svelte,
 * change their order, or add elements inbetween the components.
 * Practically speaking, we let Svelte do the initial insertion of an element, but then immediately
 * move it somewhere else, and save a reference to it.
 */
function dynamicMounting<T extends Registration, U extends Element>(
    makeRegistration: () => T,
): RegistrationAPI<T, U> {
    const registrations: T[] = [];
    const items = writable(registrations);

    function registerComponent(
        index: number = registrations.length,
        registration = makeRegistration(),
    ): T {
        items.update((registrations) => {
            registrations.splice(index, 0, registration);
            return registrations;
        });

        return registration;
    }

    const dynamicRegistrations: [DynamicSvelteComponent, T][] = [];
    const dynamicItems = writable(dynamicRegistrations);

    const [elementPromise, resolve] = promiseWithResolver<U>();
    const access = elementPromise.then(childrenAccess);

    async function addComponent(
        component: DynamicSvelteComponent,
        reinsert: (newElement: U, access: ChildrenAccess<U>) => number,
    ): Promise<void> {
        const childrenAccess = await access;
        const registration = makeRegistration();

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
                        !nodeIsElement(addedNode) ||
                        !elementIsDynamicComponent(addedNode)
                    ) {
                        continue;
                    }

                    const theElement = addedNode as U;
                    const index = reinsert(theElement, childrenAccess);

                    if (index >= 0) {
                        registerComponent(index, registration);
                    }

                    return observer.disconnect();
                }
            }
        }

        const observer = new MutationObserver(callback);
        observer.observe(childrenAccess.parent, { childList: true });

        dynamicRegistrations.push([component, registration]);
        dynamicItems.set(dynamicRegistrations);
    }

    async function updateRegistration(
        update: (registration: T) => void,
        identifier: Identifier,
    ): Promise<boolean> {
        const childrenAccess = await access;

        return childrenAccess.updateElement((_element: U, index: number): void => {
            update(registrations[index]);
            items.set(registrations);
        }, identifier);
    }

    function createInterface(
        callback: (api: CreateInterfaceAPI<T, U>) => any = defaultInterface,
    ): void {
        return callback({ addComponent, updateRegistration });
    }

    return {
        registerComponent,
        createInterface,
        resolve,
        items,
        dynamicItems,
    };
}

export default dynamicMounting;
