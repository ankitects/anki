<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "../../lib/ftl";
    import { historyIcon } from "./icons";
    import TextButton from "./TextButton.svelte";
</script>

<div
    class="history-button"
    title=""
    on:mousedown|preventDefault
>
    <TextButton>
        <span slot="label">History</span>
        <svelte:fragment slot="icon">{@html historyIcon}</svelte:fragment>
    </TextButton>
</div>
