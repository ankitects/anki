<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    export let value: string | undefined = undefined;
    export let selected = false;
</script>

<option class="select-option" {value} {selected}>
    <slot />
</option>

<style lang="scss">
    .select-option {
        background-color: var(--canvas-elevated);
    }
</style>
