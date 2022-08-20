<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import type {
        FloatingElement,
        Placement,
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
    import positionFloating from "../sveltelib/position/position-floating";
    import subscribeTrigger from "../sveltelib/subscribe-trigger";
    import FloatingArrow from "./FloatingArrow.svelte"

    export let placement: Placement | "auto" = "bottom";
    export let offset = 5;
    export let shift = 5;
    export let hideIfEscaped = false;
    export let hideIfReferenceHidden = false;

    /** This may be passed in for more fine-grained control */
    export let show = writable(true);

    let arrow: HTMLElement;

    $: positionCurried = positionFloating({
        placement,
        offset,
        shift,
        arrow,
        hideIfEscaped,
        hideIfReferenceHidden,
        show,
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

    function applyPosition(reference: HTMLElement, floating: FloatingElement, position: PositionAlgorithm): Promise<void> {
        return position(reference, floating);
    }

    async function position(
        callback: (
            reference: HTMLElement,
            floating: FloatingElement,
            position: PositionAlgorithm,
        ) => Promise<void> = applyPosition,
    ): Promise<void> {
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
                outside: true,
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

{#if floating && arrow}
    <slot {position} {asReference} />
{/if}

<div bind:this={floating} class="floating" use:portal>
    {#if $show}
        <slot name="floating" />

    {/if}

    <div bind:this={arrow} class="floating-arrow" hidden={!$show}>
        <FloatingArrow />
    </div>
</div>

<style lang="scss">
    @use "sass/elevation" as elevation;

    .floating {
        position: absolute;
        border-radius: 5px;

        z-index: 90;
        @include elevation.elevation(8);

        &-arrow {
            position: absolute;
        }
    }
</style>
