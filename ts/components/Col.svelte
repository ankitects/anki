<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import type { Breakpoint } from "./types";

    export let breakpoint: Breakpoint | undefined = undefined;
</script>

<div class="col">
    <slot />
</div>

<style lang="scss">
    .col {
        display: flex;
        flex-flow: row nowrap;
        align-items: var(--col-align, flex-start);
        justify-content: var(--col-justify, flex-start);
        flex: 1 0 calc(100% / var(--cols, 1) * var(--col-size, 1));
    }
</style>
