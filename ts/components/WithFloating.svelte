<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { writable } from "svelte/store";
    import type { Placement } from "@floating-ui/dom";
    import position from "../sveltelib/position";
    import portal from "../sveltelib/portal";
    import closeOnClick from "../sveltelib/close-on-click";

    export let placement: Placement = "bottom";

    let reference: HTMLElement;
    let floating: HTMLElement;

    let show = writable(false);
</script>

<div bind:this={reference} class="reference" use:position={{ floating, placement }}>
    <slot name="reference" {show} />
</div>

<div
    bind:this={floating}
    class="floating"
    use:portal
    use:closeOnClick={{ active: show, reference }}
    hidden={!$show}
>
    <slot name="floating" />
</div>

<style lang="scss">
    .floating {
        position: absolute;
        z-index: 90;
    }
</style>
