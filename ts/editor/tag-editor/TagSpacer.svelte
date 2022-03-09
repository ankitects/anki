<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import Tag from "./Tag.svelte";
</script>

<div class="tag-spacer" on:click />

<div class="zero-width-tag">
    <!-- makes sure footer does not resize when adding first tag -->
    <Tag>SPACER</Tag>
</div>

<style lang="scss">
    .tag-spacer {
        cursor: text;
        flex-grow: 1;
        align-self: stretch;
    }

    .zero-width-tag {
        position: relative;

        :global(.tag) {
            opacity: 0;
            width: 0;
            pointer-events: none;
            padding-left: 0 !important;
            padding-right: 0 !important;
        }
    }
</style>
