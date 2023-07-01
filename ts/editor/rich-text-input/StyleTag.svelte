<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { onDestroy } from "svelte";

    import { getCustomStylesContext } from "./CustomStyles.svelte";

    export let id: string;

    const { register, deregister } = getCustomStylesContext();

    function onLoad(event: Event): void {
        const style = event.target! as HTMLStyleElement;
        register(id, { element: style });
    }

    onDestroy(() => deregister(id));
</script>

<!-- otherwise Svelte thinks it's a scoped style tag -->
{#if true}
    <style {id} on:load={onLoad}></style>
{/if}
