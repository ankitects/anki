<script lang="ts">
    import { setupReviewer } from "./reviewer";

    let iframe: HTMLIFrameElement;

    $: ({ cardClass } = setupReviewer(iframe));
</script>

<div id="qa" class={$cardClass}>
    <iframe bind:this={iframe} title="card" frameborder="0"></iframe>
</div>

<style lang="scss">
    #qa {
        flex: 1;
    }

    iframe {
        width: 100%;
        height: 100%;
    }
</style>
