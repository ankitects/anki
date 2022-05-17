<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import TextButton from "./TextButton.svelte";
</script>

<div class="ghost-button">
    <TextButton>
        <slot name="icon" slot="icon" />
        <slot name="label" slot="label" />
    </TextButton>
</div>

<style lang="scss">
    .ghost-button {
        border: 1px solid grey;
        border-radius: 5px;

        margin-left: 4px;
        margin-right: 4px;
    }
</style>
