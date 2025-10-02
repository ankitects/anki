<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { bridgeCommand } from "@tslib/bridgecommand";
    import type { AnswerButtonInfo } from "./types";

    export let info: AnswerButtonInfo;
</script>

<span>
    {info.due}
</span>
<button on:click={() => bridgeCommand(`ease${info.i}`)}>
    {info.label}
</button>

<style>
    span {
        text-align: center;
    }
</style>