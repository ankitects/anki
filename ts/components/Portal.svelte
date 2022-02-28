<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import portal from "../sveltelib/portal";
    import closeOnClick from "../sveltelib/close-on-click";

    export let target = document.body;

    let ref: HTMLElement;
</script>

<div class="portal" bind:this={ref} use:portal={target} use:closeOnClick>
    <slot />
</div>

<slot name="outside" {ref} />

<style lang="scss">
    .portal {
        position: absolute;
        z-index: 90;
    }
</style>
