<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts"></script>

<path class="cumulative-overlay" />

<style lang="scss">
    :global(:root) {
        --area-fill: #000000;
        --area-fill-opacity: 0.03;
        --area-stroke: #000000;
        --area-stroke-opacity: 0.08;
    }

    :global(:root[class*="night-mode"]) {
        --area-fill: #ffffff;
        --area-fill-opacity: 0.08;
        --area-stroke: #000000;
        --area-stroke-opacity: 0.18;
    }

    .cumulative-overlay {
        pointer-events: none;
        fill: var(--area-fill);
        fill-opacity: var(--area-fill-opacity);
        stroke: var(--area-stroke);
        stroke-opacity: var(--area-stroke-opacity);
    }
</style>
