<script lang="ts">
    import type { Writable } from "svelte/store";

    export let html: Writable<string>
</script>

<div id="qa" class="card">
    {@html $html}
</div>

<style lang="scss">
    #qa {
        flex: 1
    }
</style>
