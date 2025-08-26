<script lang="ts">
    import { bridgeCommand } from "@tslib/bridgecommand";
    import type { AnswerButtonInfo } from "./types";

    export let info: AnswerButtonInfo
</script>

<button on:click={()=>bridgeCommand(`ease${info.i}`)}>
    {info.label}
</button>