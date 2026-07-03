<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts"></script>

<g class="hover-columns" />

<style lang="scss">
    .hover-columns {
        :global(rect) {
            fill: none;
            pointer-events: all;
        }

        :global(rect:hover) {
            fill: grey;
            opacity: 0.2;
        }
    }
</style>
