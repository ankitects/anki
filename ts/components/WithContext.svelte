<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import type { Readable } from "svelte/store";
    import { getContext } from "svelte";

    type T = boolean;

    export let key: Symbol | string;

    const store = getContext<Readable<T>>(key);
</script>

<slot context={$store} />
