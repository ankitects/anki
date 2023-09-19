<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { bridgeCommand } from "@tslib/bridgecommand";
    import * as tr from "@tslib/ftl";
    import { getPlatformString } from "@tslib/shortcuts";

    import LabelButton from "../components/LabelButton.svelte";
    import Shortcut from "../components/Shortcut.svelte";

    export let container: HTMLElement;
    const keyCombination = "Control+Enter";

    function onClose() {
        bridgeCommand("close");
    }
</script>

<div
    class="fixed-header d-flex flex-row-reverse justify-content-between desktop-only"
    bind:this={container}
>
    <LabelButton
        primary
        tooltip={getPlatformString(keyCombination)}
        on:click={onClose}
        --border-left-radius="5px"
        --border-right-radius="5px"
    >
        <div class="close">{tr.actionsClose()}</div>
    </LabelButton>
    <Shortcut {keyCombination} on:action={onClose} />
</div>

<style lang="scss">
    .fixed-header {
        position: fixed;
        right: 0;
        bottom: 0;
        z-index: 10;

        margin: 0;
        padding: 0.5rem;

        background: var(--canvas);

        .close {
            margin-inline: 0.75rem;
        }
    }
</style>
