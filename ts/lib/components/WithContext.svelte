<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { getContext } from "svelte";
    import type { Readable } from "svelte/store";

    type T = boolean;

    export let key: Symbol | string;

    const store = getContext<Readable<T>>(key);
</script>

<slot context={$store} />
