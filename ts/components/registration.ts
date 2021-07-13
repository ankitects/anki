// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
import type { SvelteComponentTyped } from "svelte/internal";
import type { Writable, Readable } from "svelte/store";
import { writable } from "svelte/store";
import type { Identifier } from "./identifier";
import { findElement } from "./identifier";

export interface SvelteComponent {
    component: SvelteComponentTyped;
    id: string;
    props: Record<string, unknown> | undefined;
}

export interface Registration {
    detach: Writable<boolean>;
}

export type Register<T extends Registration> = (index?: number, registration?: T) => T;

export interface RegistrationAPI<T extends Registration> {
    registerComponent: Register<T>;
    items: Readable<T[]>;
    dynamicItems: Readable<[SvelteComponent, T][]>;
    getDynamicInterface: (elementRef: HTMLElement) => DynamicRegistrationAPI<T>;
}

export interface DynamicRegistrationAPI<T> {
    addComponent: (
        component: SvelteComponent,
        add: (added: Element, parent: Element) => number
    ) => void;
    updateRegistration: (
        update: (registration: T) => void,
        position: Identifier
    ) => void;
}

export function nodeIsElement(node: Node): node is Element {
    return node.nodeType === Node.ELEMENT_NODE;
}

export function makeInterface<T extends Registration>(
    makeRegistration: () => T
): RegistrationAPI<T> {
    const registrations: T[] = [];
    const items = writable(registrations);

    function registerComponent(
        index: number = registrations.length,
        registration = makeRegistration()
    ): T {
        items.update((registrations) => {
            registrations.splice(index, 0, registration);
            return registrations;
        });

        return registration;
    }

    const dynamicRegistrations: [SvelteComponent, T][] = [];
    const dynamicItems = writable(dynamicRegistrations);

    function getDynamicInterface(elementRef: HTMLElement): DynamicRegistrationAPI<T> {
        function addComponent(
            component: SvelteComponent,
            add: (added: Element, parent: Element) => number
        ): void {
            const registration = makeRegistration();

            const callback = (
                mutations: MutationRecord[],
                observer: MutationObserver
            ): void => {
                for (const mutation of mutations) {
                    for (const addedNode of mutation.addedNodes) {
                        if (
                            nodeIsElement(addedNode) &&
                            (!component.id || addedNode.id === component.id)
                        ) {
                            const index = add(addedNode, elementRef);

                            if (index >= 0) {
                                registerComponent(index, registration);
                            }

                            return observer.disconnect();
                        }
                    }
                }
            };

            const observer = new MutationObserver(callback);
            observer.observe(elementRef, { childList: true });

            dynamicRegistrations.push([component, registration]);
            dynamicItems.set(dynamicRegistrations);
        }

        function updateRegistration(
            update: (registration: T) => void,
            position: Identifier
        ): void {
            const match = findElement(elementRef.children, position);

            if (match) {
                const [index] = match;
                const registration = registrations[index];
                update(registration);
                items.set(registrations);
            }
        }

        return {
            addComponent,
            updateRegistration,
        };
    }

    return {
        registerComponent,
        items,
        dynamicItems,
        getDynamicInterface,
    };
}
