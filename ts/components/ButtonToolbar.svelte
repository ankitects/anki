<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import { setContext } from "svelte";
    import { writable } from "svelte/store";
    import ButtonToolbarItem from "./ButtonToolbarItem.svelte";
    import type { ButtonGroupRegistration } from "./buttons";
    import { buttonToolbarKey } from "./contextKeys";
    import type { Identifier } from "./identifier";
    import { insert, add } from "./identifier";
    import type { SvelteComponent } from "./registration";
    import { makeInterface } from "./registration";

    export let id: string | undefined = undefined;
    let className: string = "";
    export { className as class };

    export let nowrap = false;

    function makeRegistration(): ButtonGroupRegistration {
        const detach = writable(false);
        return { detach };
    }

    const { registerComponent, dynamicItems, getDynamicInterface } = makeInterface(
        makeRegistration
    );

    setContext(buttonToolbarKey, registerComponent);

    export let api = {};
    let buttonToolbarRef: HTMLDivElement;

    $: if (buttonToolbarRef) {
        const { addComponent, updateRegistration } = getDynamicInterface(
            buttonToolbarRef
        );

        const insertGroup = (button: SvelteComponent, position: Identifier = 0) =>
            addComponent(button, (added, parent) => insert(added, parent, position));
        const appendGroup = (button: SvelteComponent, position: Identifier = -1) =>
            addComponent(button, (added, parent) => add(added, parent, position));

        const showGroup = (id: Identifier) =>
            updateRegistration(({ detach }) => detach.set(false), id);
        const hideGroup = (id: Identifier) =>
            updateRegistration(({ detach }) => detach.set(true), id);
        const toggleGroup = (id: Identifier) =>
            updateRegistration(
                ({ detach }) => detach.update((old: boolean): boolean => !old),
                id
            );

        Object.assign(api, {
            insertGroup,
            appendGroup,
            showGroup,
            hideGroup,
            toggleGroup,
        });
    }
</script>

<div
    bind:this={buttonToolbarRef}
    {id}
    class={`btn-toolbar ${className}`}
    class:flex-nowrap={nowrap}
    role="toolbar">
    <slot />
    {#each $dynamicItems as item}
        <ButtonToolbarItem id={item[0].id} registration={item[1]}>
            <svelte:component this={item[0].component} {...item[0].props} />
        </ButtonToolbarItem>
    {/each}
</div>
