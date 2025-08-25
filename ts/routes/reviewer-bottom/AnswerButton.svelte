<script lang="ts">
    import { bridgeCommand } from "@tslib/bridgecommand";

    export let info: AnswerButtonInfo
</script>

<button on:click={()=>bridgeCommand(`ease${info.i}`)}>
    {info.label}
</button>