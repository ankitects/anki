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

    export let size: number | undefined = undefined;
    export let wrap: boolean | undefined = undefined;

    $: buttonSize = size ? `--buttons-size: ${size}rem; ` : "";
    let buttonWrap: string;
    $: if (wrap === undefined) {
        buttonWrap = "";
    } else {
        buttonWrap = wrap ? `--buttons-wrap: wrap; ` : `--buttons-wrap: nowrap; `;
    }

    $: style = buttonSize + buttonWrap;

    function makeRegistration(): ButtonGroupRegistration {
        const detach = writable(false);
        return { detach };
    }

    const { registerComponent, dynamicItems, getDynamicInterface } =
        makeInterface(makeRegistration);

    setContext(buttonToolbarKey, registerComponent);

    export let api: Record<string, unknown> | undefined = undefined;
    let buttonToolbarRef: HTMLDivElement;

    $: if (buttonToolbarRef && api) {
        const { addComponent, updateRegistration } =
            getDynamicInterface(buttonToolbarRef);

        const insertGroup = (group: SvelteComponent, position: Identifier = 0) =>
            addComponent(group, (added, parent) => insert(added, parent, position));
        const appendGroup = (group: SvelteComponent, position: Identifier = -1) =>
            addComponent(group, (added, parent) => add(added, parent, position));

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
    class={`btn-toolbar wrap-variable ${className}`}
    {style}
    role="toolbar"
>
    <slot />
    {#each $dynamicItems as item}
        <ButtonToolbarItem id={item[0].id} registration={item[1]}>
            <svelte:component this={item[0].component} {...item[0].props} />
        </ButtonToolbarItem>
    {/each}
</div>

<style lang="scss">
    .wrap-variable {
        flex-wrap: var(--buttons-wrap);
    }
</style>
