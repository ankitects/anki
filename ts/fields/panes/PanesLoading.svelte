<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    export let barCount = 10;

    $: bars = Array.from(Array(barCount).keys());
</script>

<div class="panes-loading">
    {#each bars as bar}
        <div class="bar" style:animation-delay="{bar * 0.1}s" />
    {/each}
</div>

<style lang="scss">
    @use "sass/elevation" as elevation;

    .panes-loading {
        display: flex;
        margin: auto;
        padding: 5rem;
    }

    .bar {
        width: 15px;
        height: 168px;
        margin: 1px;
        border-radius: 8px;
        animation: scaleUpDown 1.5s linear infinite;
        transform-origin: bottom center;
    }

    @keyframes scaleUpDown {
        0% {
            transform: scaleY(0);
            background: transparent;
            /* box-shadow is here so it does not cast
             * an ugly initial shadow */
            @include elevation.elevation(1);
        }
        50% {
            transform: scaleY(1);
            background: cornflowerblue;
            @include elevation.elevation(1);
        }
        100% {
            transform: scaleY(0);
            background: transparent;
            @include elevation.elevation(1);
        }
    }
</style>
