<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { onDestroy } from "svelte";

    import { getCustomStylesContext } from "./CustomStyles.svelte";

    export let id: string;
    export let href: string;

    const { register, deregister } = getCustomStylesContext();

    function onLoad(event: Event): void {
        const link = event.target! as HTMLLinkElement;
        register(id, { element: link });
    }

    onDestroy(() => deregister(id));
</script>

<link {id} rel="stylesheet" {href} on:load={onLoad} />
