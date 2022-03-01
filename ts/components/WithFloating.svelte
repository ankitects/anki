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
    import { documentClick, documentKeyup } from "../sveltelib/event-store";
    import isClosingClick from "../sveltelib/closing-click";
    import isClosingKeyup from "../sveltelib/closing-keyup";
    import subscribeTrigger from "../sveltelib/subscribe-trigger";
    import toggleable from "../sveltelib/toggleable";

    export let placement: Placement = "bottom";
    export let closeOnInsideClick = false;

    /** This may be passed in for more fine-grained control */
    export let show = writable(false);

    let reference: HTMLElement;
    let floating: HTMLElement;
    let arrow: HTMLElement;

    const { toggle, on, off } = toggleable(show);

    onMount(() =>
        subscribeTrigger(
            show,
            isClosingClick(documentClick, {
                reference,
                floating,
                inside: closeOnInsideClick,
                outside: true,
            }),
            isClosingKeyup(documentKeyup, {
                reference,
                floating,
            }),
        ),
    );
</script>

<div
    bind:this={reference}
    class="reference"
    use:position={{ floating: $show ? floating : null, placement, arrow }}
>
    <slot name="reference" {show} {toggle} {on} {off} />
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
