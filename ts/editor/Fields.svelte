<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import ScrollArea from "$lib/components/ScrollArea.svelte";
</script>

<!--
@component
Contains the fields. This contains the scrollable area.
-->
<ScrollArea>
    <div class="fields">
        <slot />
    </div>
</ScrollArea>

<style lang="scss">
    .fields {
        margin-top: 5px;
        display: grid;
        grid-auto-rows: min-content;
        grid-gap: 6px;

        /* Add space after the last field and the start of the tag editor */
        padding-bottom: 5px;

        /* Move the scrollbar for the NoteEditor into this element */
        position: relative;
        overflow-y: auto;

        /* Push the tag editor to the bottom of the note editor */
        flex-grow: 1;
    }
</style>
