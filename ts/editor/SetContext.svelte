<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import type { EditorContext } from "./context";
    import { setContext } from "./context";

    export let key: symbol;
    export let value: any;

    setContext(key, value as EditorContext<typeof key>);
</script>

<slot />
