<script lang="ts">
    import type { Writable } from "svelte/store";

    export let html: Writable<string>
    export let cardClass: Writable<string>
</script>

<div id="qa" class={$cardClass}>
    {@html $html}
</div>

<style lang="scss">
    #qa {
        flex: 1
    }
</style>
