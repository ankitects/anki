<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import type {
        FloatingElement,
    } from "@floating-ui/dom";
    import type { ActionReturn } from "svelte/action";
    import { writable } from "svelte/store";

    import type { Callback } from "../lib/typing"
    import { singleCallback } from "../lib/typing";
    import isClosingClick from "../sveltelib/closing-click";
    import isClosingKeyup from "../sveltelib/closing-keyup";
    import { documentClick, documentKeyup } from "../sveltelib/event-store";
    import portal from "../sveltelib/portal";
    import type { PositioningCallback } from "../sveltelib/position/auto-update"
    import autoUpdate from "../sveltelib/position/auto-update"
    import type { PositionAlgorithm } from "../sveltelib/position/position-algorithm";
    import positionOverlay from "../sveltelib/position/position-overlay";
    import subscribeTrigger from "../sveltelib/subscribe-trigger";

    export let padding = 0;

    /** This may be passed in for more fine-grained control */
    export let show = writable(true);

    $: positionCurried = positionOverlay({
        show,
        padding,
    });

    let actionReturn: ActionReturn = {};

    $: {
        positionCurried;
        actionReturn.update?.(positioningCallback);
    }

    export let closeOnInsideClick = false;
    export let keepOnKeyup = false;

    export let reference: HTMLElement | undefined = undefined;
    let floating: FloatingElement;

    async function position(callback: (reference: HTMLElement, floating: FloatingElement, position: PositionAlgorithm) => Promise<void>): Promise<void> {
        if (reference && floating) {
            return callback(reference, floating, positionCurried);
        }
    }

    function asReference(referenceArgument: HTMLElement) {
        reference = referenceArgument;
    }

    function positioningCallback(reference: HTMLElement, callback: PositioningCallback): Callback {
        const innerFloating = floating;
        return callback(reference, innerFloating, () => positionCurried(reference, innerFloating))
    }

    let cleanup: Callback;

    function updateFloating(
        reference: HTMLElement | undefined,
        floating: FloatingElement,
        isShowing: boolean
    ) {
        cleanup?.();

        if (!reference || !floating || !isShowing) {
            return;
        }

        const triggers = [
            isClosingClick(documentClick, {
                reference,
                floating,
                inside: closeOnInsideClick,
                outside: false,
            }),
        ];

        if (!keepOnKeyup) {
            triggers.push(
                isClosingKeyup(documentKeyup, {
                    reference,
                    floating,
                }),
            );
        }

        actionReturn = autoUpdate(reference, positioningCallback);
        cleanup = singleCallback(
            subscribeTrigger(show, ...triggers),
            actionReturn.destroy!,
        );
    }

    $: updateFloating(reference, floating, $show);
</script>

{#if floating}
    <slot {position} {asReference} />
{/if}

<div bind:this={floating} class="overlay" use:portal>
    {#if $show}
        <slot name="overlay" />
    {/if}
</div>

<style lang="scss">
    @use "sass/elevation" as elevation;

    .overlay {
        position: absolute;
        border-radius: 5px;

        z-index: 90;
        @include elevation.elevation(5);
    }
</style>
