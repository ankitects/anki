<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script context="module">
    import { DefineArray } from "editable/decorated";

    const decoratedComponents = new DefineArray();
</script>

<script>
    import { setContext } from "svelte";
    import { decoratedComponentsKey } from "lib/context-keys";

    setContext(decoratedComponentsKey, decoratedComponents);
</script>

<slot />
