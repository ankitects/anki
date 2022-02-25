<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { onMount } from "svelte";
    import * as float from "@floating-ui/dom";

    export let floating: HTMLElement;

    let ref: HTMLElement;

    onMount(() => float.computePosition(ref, floating, {}));
</script>

<div bind:this={ref}>
    <slot />
</div>
