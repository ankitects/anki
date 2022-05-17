<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { bridgeCommand } from "../../lib/bridgecommand";
    import * as tr from "../../lib/ftl";
    import { fieldIcon } from "./icons";
    import TextButton from "./TextButton.svelte";
</script>

<div
    class="fields-button"
    title={tr.editingCustomizeFields()}
    on:click={() => bridgeCommand("fields")}
    on:mousedown|preventDefault
>
    <TextButton>
        <span slot="label">{tr.editingFields()}</span>
        <svelte:fragment slot="icon">{@html fieldIcon}</svelte:fragment>
    </TextButton>
</div>
