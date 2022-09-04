<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import type { FloatingElement, Placement } from "@floating-ui/dom";
    import { createEventDispatcher, onDestroy } from "svelte";
    import type { ActionReturn } from "svelte/action";

    import type { Callback } from "../lib/typing";
    import { singleCallback } from "../lib/typing";
    import isClosingClick from "../sveltelib/closing-click";
    import isClosingKeyup from "../sveltelib/closing-keyup";
    import { documentClick, documentKeyup } from "../sveltelib/event-store";
    import portal from "../sveltelib/portal";
    import type { PositioningCallback } from "../sveltelib/position/auto-update";
    import autoUpdate from "../sveltelib/position/auto-update";
    import type { PositionAlgorithm } from "../sveltelib/position/position-algorithm";
    import positionFloating from "../sveltelib/position/position-floating";
    import subscribeToUpdates from "../sveltelib/subscribe-updates";
    import FloatingArrow from "./FloatingArrow.svelte";

    export let placement: Placement | "auto" = "bottom";
    export let offset = 5;
    export let shift = 5;
    export let inline = false;
    export let hideIfEscaped = false;
    export let hideIfReferenceHidden = false;

    /** This may be passed in for more fine-grained control */
    export let show = true;

    const dispatch = createEventDispatcher();

    function notify(reason: symbol) {
        dispatch("close", reason);
    }

    let arrow: HTMLElement;

    $: positionCurried = positionFloating({
        placement,
        offset,
        shift,
        inline,
        arrow,
        hideIfEscaped,
        hideIfReferenceHidden,
        hideCallback: notify,
    });

    let autoAction: ActionReturn = {};

    $: {
        positionCurried;
        autoAction.update?.(positioningCallback);
    }

    export let closeOnInsideClick = false;
    export let keepOnKeyup = false;

    export let reference: HTMLElement | undefined = undefined;
    let floating: FloatingElement;

    function applyPosition(
        reference: HTMLElement,
        floating: FloatingElement,
        position: PositionAlgorithm,
    ): Promise<void> {
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

    function positioningCallback(
        reference: HTMLElement,
        callback: PositioningCallback,
    ): Callback {
        const innerFloating = floating;
        return callback(reference, innerFloating, () =>
            positionCurried(reference, innerFloating),
        );
    }

    let cleanup: Callback | null = null;

    function updateFloating(
        reference: HTMLElement | undefined,
        floating: FloatingElement,
        isShowing: boolean,
    ) {
        cleanup?.();
        cleanup = null;

        if (!reference || !floating || !isShowing) {
            return;
        }

        const closingClick = isClosingClick(documentClick, {
            reference,
            floating,
            inside: closeOnInsideClick,
            outside: true,
        });

        const subscribers = [subscribeToUpdates(closingClick, notify)];

        if (!keepOnKeyup) {
            const closingKeyup = isClosingKeyup(documentKeyup, {
                reference,
                floating,
            });

            subscribers.push(subscribeToUpdates(closingKeyup, notify));
        }

        autoAction = autoUpdate(reference, positioningCallback);
        cleanup = singleCallback(...subscribers, autoAction.destroy!);
    }

    $: updateFloating(reference, floating, show);

    onDestroy(() => cleanup?.());
</script>

<slot {position} {asReference} />

{#if $$slots.reference}
    {#if inline}
        <span class="floating-reference" use:asReference>
            <slot name="reference" />
        </span>
    {:else}
        <div class="floating-reference" use:asReference>
            <slot name="reference" />
        </div>
    {/if}
{/if}

<div bind:this={floating} class="floating">
    {#if show}
        <slot name="floating" />
    {/if}

    <div bind:this={arrow} class="floating-arrow" hidden={!show}>
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
