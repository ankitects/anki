<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "@generated/ftl";
    import { getPlatformString } from "@tslib/shortcuts";
    import LabelButton from "$lib/components/LabelButton.svelte";
    import Shortcut from "$lib/components/Shortcut.svelte";

    const rtl: boolean = window.getComputedStyle(document.body).direction == "rtl";

    export let onClose: () => void;
    const closeKeyCombination = "Control+Enter";
</script>

<LabelButton
    primary
    on:click={onClose}
    tooltip={getPlatformString(closeKeyCombination)}
    --border-left-radius={!rtl ? "var(--border-radius)" : "0"}
    --border-right-radius={rtl ? "var(--border-radius)" : "0"}
>
    <div class="close">{tr.actionsClose()}</div>
</LabelButton>
<Shortcut keyCombination={closeKeyCombination} on:action={onClose} />

<style lang="scss">
    .close {
        margin: 0 0.75rem;
    }
</style>
