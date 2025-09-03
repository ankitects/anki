<script lang="ts">
    import type { Writable } from "svelte/store";

    export let html: Writable<string>
</script>

<div class="qa">
    {@html $html}
</div>

<style lang="scss">
    .qa {
        flex: 1
    }
</style>
