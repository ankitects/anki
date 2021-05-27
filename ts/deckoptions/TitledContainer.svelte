<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    export let title: string;
</script>

<div class="container-fluid mb-3">
    <h1 class="mt-3">{title}</h1>
    <slot />
</div>

<style lang="scss">
    h1 {
        border-bottom: 1px solid var(--medium-border);
    }
</style>
