<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import portal from "../sveltelib/portal";

    export let target = document.body;

    let ref: HTMLElement;
</script>

<div class="portal" bind:this={ref} use:portal={target} hidden>
    <slot />
</div>

<slot name="outside" {ref} />

<style lang="scss">
    .portal {
        display: contents;
    }
</style>
