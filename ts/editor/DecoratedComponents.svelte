<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script context="module">
    import { DefineArray } from "editable/decorated";

    const decoratedComponents = new DefineArray();
</script>

<script lang="ts">
    import { getContext } from "svelte";

    export let contextKey: Symbol;

    const context = getContext(contextKey);

    Object.defineProperty(context, "decoratedComponents", decoratedComponents);
</script>

<slot />
