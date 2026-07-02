<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    let className: string = "";
    export { className as class };
</script>

<div class="row {className}">
    <slot />
</div>

<style lang="scss">
    .row {
        display: flex;
        flex-flow: row wrap;
        align-content: stretch;
        padding: var(--gutter-block, 0) 0;
        align-items: center;
    }
</style>
