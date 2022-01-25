<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { setContext } from "svelte";
    import { writable } from "svelte/store";
    import type { Registration } from "../sveltelib/registration";
    import dynamicMounting from "../sveltelib/registration";
    import Item from "./Item.svelte";
    import { sectionKey } from "./context-keys";

    export let id: string | undefined = undefined;

    function makeRegistration(): Registration {
        const detach = writable(false);
        return { detach };
    }

    const { dynamicItems, registerComponent, createInterface } =
        dynamicMounting(makeRegistration);

    setContext(sectionKey, registerComponent);

    export let api: Record<string, never> | undefined = undefined;

    if (api) {
        Object.assign(api, createInterface());
    }
</script>

<div class="section" {id}>
    <slot />
    {#each $dynamicItems as item}
        <Item id={item[0].id} registration={item[1]}>
            <svelte:component this={item[0].component} {...item[0].props} />
        </Item>
    {/each}
</div>

<style lang="scss">
    .section {
        display: contents;
    }
</style>
