<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import Detachable from "components/Detachable.svelte";

    import { getContext } from "svelte";
    import { buttonGroupKey } from "./contextKeys";

    const { registerButton } = getContext(buttonGroupKey);
    const { detach } = registerButton();
</script>

<Detachable detach={$detach}>
    <slot />
</Detachable>
