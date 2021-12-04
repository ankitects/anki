<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script context="module" lang="ts">
    import type { Identifier } from "./identifier";
    import type { SvelteComponent } from "./registration";

    export interface ButtonToolbarAPI {
        insertGroup(button: SvelteComponent, position: Identifier): void;
        appendGroup(button: SvelteComponent, position: Identifier): void;
        showGroup(position: Identifier): void;
        hideGroup(position: Identifier): void;
        toggleGroup(position: Identifier): void;
    }
</script>

<script lang="ts">
    import { setContext } from "svelte";
    import { writable } from "svelte/store";
    import Item from "./Item.svelte";
    import type { Registration } from "./registration";
    import { sectionKey } from "./context-keys";
    import { insertElement, appendElement } from "./identifier";
    import { makeInterface } from "./registration";
    import { pageTheme } from "../sveltelib/theme";

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

    function makeRegistration(): Registration {
        const detach = writable(false);
        return { detach };
    }

    const { registerComponent, dynamicItems, getDynamicInterface } =
        makeInterface(makeRegistration);

    setContext(sectionKey, registerComponent);

    export let api: Partial<ButtonToolbarAPI> | undefined = undefined;
    let buttonToolbarRef: HTMLDivElement;

    function createApi(): void {
        const { addComponent, updateRegistration } =
            getDynamicInterface(buttonToolbarRef);

        const insertGroup = (group: SvelteComponent, position: Identifier = 0) =>
            addComponent(group, (added, parent) =>
                insertElement(added, parent, position),
            );
        const appendGroup = (group: SvelteComponent, position: Identifier = -1) =>
            addComponent(group, (added, parent) =>
                appendElement(added, parent, position),
            );

        const showGroup = (id: Identifier) =>
            updateRegistration(({ detach }) => detach.set(false), id);
        const hideGroup = (id: Identifier) =>
            updateRegistration(({ detach }) => detach.set(true), id);
        const toggleGroup = (id: Identifier) =>
            updateRegistration(
                ({ detach }) => detach.update((old: boolean): boolean => !old),
                id,
            );

        Object.assign(api, {
            insertGroup,
            appendGroup,
            showGroup,
            hideGroup,
            toggleGroup,
        });
    }

    $: if (buttonToolbarRef && api) {
        createApi();
    }
</script>

<div
    bind:this={buttonToolbarRef}
    {id}
    class="button-toolbar btn-toolbar {className}"
    class:nightMode={$pageTheme.isDark}
    {style}
    role="toolbar"
    on:focusout
>
    <slot />
    {#each $dynamicItems as item}
        <Item id={item[0].id} registration={item[1]}>
            <svelte:component this={item[0].component} {...item[0].props} />
        </Item>
    {/each}
</div>

<style lang="scss">
    .button-toolbar {
        flex-wrap: var(--buttons-wrap);
        padding-left: 0.15rem;

        > :global(*) > :global(*) {
            /* TODO replace with gap once available */
            margin-right: 0.15rem;
            margin-bottom: 0.15rem;
        }
    }
</style>
