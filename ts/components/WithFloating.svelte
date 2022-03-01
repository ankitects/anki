<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { onMount } from "svelte";
    import { writable } from "svelte/store";
    import type { Placement } from "@floating-ui/dom";
    import position from "../sveltelib/position";
    import portal from "../sveltelib/portal";
    import { documentClick } from "../sveltelib/event-store";
    import isClosingClick from "../sveltelib/closing-click";
    import subscribeTrigger from "../sveltelib/subscribe-trigger";

    export let placement: Placement = "bottom";
    export let closeOnFloatingClick: boolean = false;

    let reference: HTMLElement;
    let floating: HTMLElement;
    let arrow: HTMLElement;

    let show = writable(false);

    onMount(() =>
        subscribeTrigger(
            show,
            isClosingClick(documentClick, {
                reference,
                floating,
                inside: closeOnFloatingClick,
                outside: true,
            }),
        ),
    );
</script>

<div
    bind:this={reference}
    class="reference"
    use:position={{ placement, floating, arrow }}
>
    <slot name="reference" {show} />
</div>

<div bind:this={floating} class="floating" hidden={!$show} use:portal>
    <slot name="floating" />

    <div bind:this={arrow} class="arrow" />
</div>

<style lang="scss">
    @use "sass/elevation" as elevation;

    .reference {
        /* TODO This should not be necessary */
        line-height: normal;
    }

    .floating {
        position: absolute;
        border-radius: 5px;

        z-index: 90;
        @include elevation.elevation(8);
    }

    .arrow {
        position: absolute;
        background-color: var(--frame-bg);
        width: 10px;
        height: 10px;
        transform: rotate(45deg);

        z-index: -1;
        @include elevation.elevation(8);
    }
</style>
