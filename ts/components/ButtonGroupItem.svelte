<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import { getContext } from "svelte";
    import { buttonGroupKey } from "./contextKeys";

    const { registerButton } = getContext(buttonGroupKey);
    const { order } = registerButton();
</script>

<slot {order} />
