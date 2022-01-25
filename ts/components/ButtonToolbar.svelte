<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script context="module" lang="ts">
    import type { Identifier } from "../lib/children-access";
    import type { DynamicSvelteComponent } from "../sveltelib/registration";

    export interface ButtonToolbarAPI {
        insertGroup(button: DynamicSvelteComponent, position: Identifier): void;
        appendGroup(button: DynamicSvelteComponent, position: Identifier): void;
        showGroup(position: Identifier): void;
        hideGroup(position: Identifier): void;
        toggleGroup(position: Identifier): void;
    }
</script>

<script lang="ts">
    import { setContext } from "svelte";
    import { writable } from "svelte/store";
    import Item from "./Item.svelte";
    import type { Registration } from "../sveltelib/registration";
    import dynamicMounting from "../sveltelib/registration";
    import { sectionKey } from "./context-keys";
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

    export let api: Partial<ButtonToolbarAPI> | undefined = undefined;

    function makeRegistration(): Registration {
        const detach = writable(false);
        return { detach };
    }

    const { dynamicItems, registerComponent, createInterface, resolve } =
        dynamicMounting(makeRegistration);

    if (api) {
        setContext(sectionKey, registerComponent);
        Object.assign(api, createInterface());
    }
</script>

<div
    {id}
    class="button-toolbar btn-toolbar {className}"
    class:nightMode={$pageTheme.isDark}
    {style}
    role="toolbar"
    on:focusout
    use:resolve
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
