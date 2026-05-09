<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    export let top: boolean = false;
    export let bottom: boolean = false;
    export let left: boolean = false;
    export let right: boolean = false;
</script>

<div class="absolute" class:top class:bottom class:left class:right>
    <slot />
</div>

<style lang="scss">
    .absolute {
        position: absolute;
        margin: var(--margin, 0);
        z-index: 20;
    }

    .top {
        top: 0;
    }

    .bottom {
        bottom: 0;
    }

    .left {
        left: 0;
    }

    .right {
        right: 0;
    }
</style>
