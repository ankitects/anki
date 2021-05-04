<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import type { SvelteComponentTyped } from "svelte";
    import { setContext } from "svelte";
    import type { Writable } from "svelte/store";
    import { writable } from "svelte/store";
    import { buttonGroupKey } from "./contextKeys";
    import type { Identifier } from "./identifier";
    import { insert, add, update } from "./identifier";

    export let id: string | undefined = undefined;
    let className: string = "";
    export { className as class };

    export let api = {};
    export let buttonGroupRef: HTMLDivElement;
    $: root = buttonGroupRef?.getRootNode() as Document;

    interface ButtonRegistration {
        detach: Writable<boolean>;
    }

    const items: ButtonRegistration[] = [];

    function registerButton(): ButtonRegistration {
        const detach = writable(false);
        const registration = { detach };
        items.push(registration);

        return registration;
    }

    let dynamic: SvelteComponentTyped[] = [];

    function addButton(
        button: SvelteComponentTyped,
        add: (added: Element, parent: Element) => number
    ): void {
        const callback = (
            mutations: MutationRecord[],
            observer: MutationObserver
        ): void => {
            for (const mutation of mutations) {
                const addedNode = mutation.addedNodes[0];

                if (addedNode.nodeType === Node.ELEMENT_NODE) {
                    const index = add(addedNode as Element, buttonGroupRef);
                }
            }

            observer.disconnect();
        };

        const observer = new MutationObserver(callback);
        observer.observe(buttonGroupRef, { childList: true });

        dynamic = [...dynamic, button];
    }

    const insertButton = (button: SvelteComponentTyped, id: Identifier = 0) =>
        addButton(button, (added, parent) => insert(added, parent, id));
    const appendButton = (button: SvelteComponentTyped, id: Identifier = -1) =>
        addButton(button, (added, parent) => add(added, parent, id));
    const showButton = (id: Identifier) =>
        update((element) => element.removeAttribute("hidden"), buttonGroupRef, id);
    const hideButton = (id: Identifier) =>
        update((element) => element.setAttribute("hidden", ""), buttonGroupRef, id);
    const toggleButton = (id: Identifier) =>
        update((element) => element.toggleAttribute("hidden"), buttonGroupRef, id);

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
    {#each dynamic as component}
        <svelte:component this={component} />
    {/each}
</div>
