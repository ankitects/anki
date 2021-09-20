<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    let editingArea: HTMLElement;
</script>

<div bind:this={editingArea} class="editing-area">
    <slot {editingArea} />
</div>

<style>
    .editing-area {
        position: relative;

        background: var(--frame-bg);
        border-radius: 0 0 5px 5px;

        /* TODO move this up one layer */
        /* &.dupe { */
        /*     // this works around the background colour persisting in copy+paste */
        /*     // (https://github.com/ankitects/anki/pull/1278) */
        /*     background-image: linear-gradient(var(--flag1-bg), var(--flag1-bg)); */
        /* } */
    }
</style>
