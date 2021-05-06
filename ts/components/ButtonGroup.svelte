<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import ButtonGroupItem from "./ButtonGroupItem.svelte";
    import type { SvelteComponentTyped } from "svelte";
    import { setContext } from "svelte";
    import { writable } from "svelte/store";
    import { buttonGroupKey } from "./contextKeys";
    import type { Identifier } from "./identifier";
    import { insert, add, find } from "./identifier";
    import type { ButtonRegistration } from "./buttons";
    import { ButtonPosition } from "./buttons";

    export let id: string | undefined = undefined;
    let className: string = "";
    export { className as class };

    export let api = {};
    let buttonGroupRef: HTMLDivElement;

    let items: ButtonRegistration[] = [];

    $: {
        for (const [index, item] of items.entries()) {
            if (items.length === 1) {
                item.position.set(ButtonPosition.Standalone);
            } else if (index === 0) {
                item.position.set(ButtonPosition.Leftmost);
            } else if (index === items.length - 1) {
                item.position.set(ButtonPosition.Rightmost);
            } else {
                item.position.set(ButtonPosition.Center);
            }
        }
    }

    function makeRegistration(): ButtonRegistration {
        const detach = writable(false);
        const position = writable(ButtonPosition.Standalone);
        return { detach, position };
    }

    function registerButton(
        index = items.length,
        registration = makeRegistration()
    ): ButtonRegistration {
        items.splice(index, 0, registration);
        items = items;
        return registration;
    }

    const dynamicItems: ButtonRegistration[] = [];
    let dynamic: SvelteComponentTyped[] = [];

    function addButton(
        button: SvelteComponentTyped,
        add: (added: Element, parent: Element) => number
    ): void {
        const registration = makeRegistration();

        const callback = (
            mutations: MutationRecord[],
            observer: MutationObserver
        ): void => {
            for (const mutation of mutations) {
                const addedNode = mutation.addedNodes[0];

                if (addedNode.nodeType === Node.ELEMENT_NODE) {
                    const index = add(addedNode as Element, buttonGroupRef);

                    if (index >= 0) {
                        registerButton(index, registration);
                    }
                }
            }

            observer.disconnect();
        };

        const observer = new MutationObserver(callback);
        observer.observe(buttonGroupRef, { childList: true });

        dynamicItems.push(registration);
        dynamic = [...dynamic, button];
    }

    const insertButton = (button: SvelteComponentTyped, id: Identifier = 0) =>
        addButton(button, (added, parent) => insert(added, parent, id));
    const appendButton = (button: SvelteComponentTyped, id: Identifier = -1) =>
        addButton(button, (added, parent) => add(added, parent, id));

    function updateRegistration(
        f: (registration: ButtonRegistration) => void,
        id: Identifier
    ): void {
        const match = find(buttonGroupRef.children, id);

        if (match) {
            const [index] = match;
            const registration = items[index];
            f(registration);
        }
    }

    const showButton = (id: Identifier) =>
        updateRegistration(({ detach }) => detach.set(false), id);
    const hideButton = (id: Identifier) =>
        updateRegistration(({ detach }) => detach.set(true), id);
    const toggleButton = (id: Identifier) =>
        updateRegistration(({ detach }) => detach.update((old) => !old), id);

    setContext(
        buttonGroupKey,
        Object.assign(api, {
            registerButton,
            insertButton,
            appendButton,
            showButton,
            hideButton,
            toggleButton,
        })
    );
</script>

<style lang="scss">
    div {
        display: flex;
        justify-items: start;
        flex-wrap: var(--toolbar-wrap);

        padding: calc(var(--toolbar-size) / 10);
        margin: 0;
    }
</style>

<div bind:this={buttonGroupRef} {id} class={className} dir="ltr">
    <slot />
    {#each dynamic as component, i}
        <ButtonGroupItem registration={dynamicItems[i]}>
            <svelte:component this={component} />
        </ButtonGroupItem>
    {/each}
</div>
