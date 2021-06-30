<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import { setContext } from "svelte";
    import { writable } from "svelte/store";
    import Item from "./Item.svelte";
    import { sectionKey } from "./context-keys";
    import type { Identifier } from "./identifier";
    import { insertElement, appendElement } from "./identifier";
    import type { SvelteComponent, Registration } from "./registration";
    import { makeInterface } from "./registration";

    export let id: string | undefined = undefined;

    function makeRegistration(): Registration {
        const detach = writable(false);
        return { detach };
    }

    const { registerComponent, dynamicItems, getDynamicInterface } =
        makeInterface(makeRegistration);

    setContext(sectionKey, registerComponent);

    export let api: Record<string, never> | undefined = undefined;
    let sectionRef: HTMLDivElement;

    $: if (sectionRef && api) {
        const { addComponent, updateRegistration } = getDynamicInterface(sectionRef);

        const insert = (group: SvelteComponent, position: Identifier = 0) =>
            addComponent(group, (added, parent) =>
                insertElement(added, parent, position)
            );
        const append = (group: SvelteComponent, position: Identifier = -1) =>
            addComponent(group, (added, parent) =>
                appendElement(added, parent, position)
            );

        const show = (id: Identifier) =>
            updateRegistration(({ detach }) => detach.set(false), id);
        const hide = (id: Identifier) =>
            updateRegistration(({ detach }) => detach.set(true), id);
        const toggle = (id: Identifier) =>
            updateRegistration(
                ({ detach }) => detach.update((old: boolean): boolean => !old),
                id
            );

        Object.assign(api, { insert, append, show, hide, toggle });
    }
</script>

<div bind:this={sectionRef} {id}>
    <slot />
    {#each $dynamicItems as item}
        <Item id={item[0].id} registration={item[1]}>
            <svelte:component this={item[0].component} {...item[0].props} />
        </Item>
    {/each}
</div>

<style lang="scss">
    div {
        display: contents;
    }
</style>
