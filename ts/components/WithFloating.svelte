<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import type { Placement } from "@floating-ui/dom";
    import position, { closeOnClick, closeOnKeyup } from "../sveltelib/position";

    export let floating: HTMLElement;
    export let placement: Placement = "bottom";
</script>

<svelte:body on:click={closeOnClick} on:keyup={closeOnKeyup} />

<div use:position={{ floating, placement }}>
    <slot />
</div>
