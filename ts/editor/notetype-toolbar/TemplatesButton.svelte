<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import Shortcut from "../../components/Shortcut.svelte";
    import { bridgeCommand } from "../../lib/bridgecommand";
    import * as tr from "../../lib/ftl";
    import { getPlatformString } from "../../lib/shortcuts";
    import { templateIcon } from "./icons";
    import TextButton from "./TextButton.svelte";

    const keyCombination = "Control+L";

    function evokeTemplatesMenu() {
        bridgeCommand("cards");
    }
</script>

<div
    class="templates-button"
    title="{tr.editingCustomizeCardTemplates()} ({getPlatformString(keyCombination)})"
    on:mousedown|preventDefault
    on:click={evokeTemplatesMenu}
>
    <TextButton>
        <span slot="label">{tr.editingTemplates()}</span>
        <svelte:fragment slot="icon">{@html templateIcon}</svelte:fragment>
    </TextButton>
</div>

<Shortcut {keyCombination} on:action={evokeTemplatesMenu} />
