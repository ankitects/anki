<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { getContext, onDestroy } from "svelte";

    import { customStylesKey } from "./CustomStyles.svelte";

    export let id: string;
    export let href: string;

    const { register, deregister } = getContext(customStylesKey);

    function onLoad(event: Event): void {
        const link = event.target! as HTMLLinkElement;
        register(id, { element: link });
    }

    onDestroy(() => deregister(id));
</script>

<link {id} rel="stylesheet" {href} on:load={onLoad} />
