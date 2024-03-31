<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import "./base.scss";

    import { setContext } from "svelte";

    import { modalsKey, touchDeviceKey } from "$lib/components/context-keys";

    setContext(modalsKey, new Map());
    setContext(touchDeviceKey, "ontouchstart" in document.documentElement);
</script>

<slot />
