<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import type {
        FloatingElement,
        Placement,
        ReferenceElement,
    } from "@floating-ui/dom";
    import type { Callback } from "@tslib/typing";
    import { singleCallback } from "@tslib/typing";
    import { createEventDispatcher, setContext } from "svelte";
    import type { ActionReturn } from "svelte/action";
    import { writable } from "svelte/store";

    import isClosingClick from "$lib/sveltelib/closing-click";
    import isClosingKeyup from "$lib/sveltelib/closing-keyup";
    import type { EventPredicateResult } from "$lib/sveltelib/event-predicate";
    import { documentClick, documentKeyup } from "$lib/sveltelib/event-store";
    import type { PositioningCallback } from "$lib/sveltelib/position/auto-update";
    import autoUpdate from "$lib/sveltelib/position/auto-update";
    import type { PositionAlgorithm } from "$lib/sveltelib/position/position-algorithm";
    import positionOverlay from "$lib/sveltelib/position/position-overlay";
    import subscribeToUpdates from "$lib/sveltelib/subscribe-updates";

    import { overlayKey } from "./context-keys";

    /* Used by Popover to set animation direction depending on placement */
    const placementPromise = writable<Promise<Placement> | undefined>();
    setContext(overlayKey, placementPromise);

    export let padding = 0;
    export let inline = false;

    /** This may be passed in for more fine-grained control */
    export let show = true;

    const dispatch = createEventDispatcher();

    $: positionCurried = positionOverlay({
        padding,
        inline,
        hideCallback: (reason: string) => dispatch("close", { reason }),
    });

    let autoAction: ActionReturn<any> = {};

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
    ): Promise<Placement> {
        const promise = position(reference, floating);
        $placementPromise = promise;
        return promise;
    }

    async function position(
        callback: (
            reference: HTMLElement,
            floating: FloatingElement,
            position: PositionAlgorithm,
        ) => Promise<Placement> = applyPosition,
    ): Promise<Placement | void> {
        if (reference && floating) {
            return callback(reference, floating, positionCurried);
        }
    }

    function asReference(referenceArgument: HTMLElement) {
        reference = referenceArgument;
    }

    function positioningCallback(
        reference: ReferenceElement,
        callback: PositioningCallback,
    ): Callback {
        const innerFloating = floating;
        return callback(reference, innerFloating, () => {
            $placementPromise = positionCurried(reference, innerFloating);
        });
    }

    let cleanup: Callback;

    function updateFloating(
        reference: HTMLElement | undefined,
        floating: FloatingElement,
        isShowing: boolean,
    ) {
        cleanup?.();

        if (!reference || !floating || !isShowing) {
            return;
        }

        const closingClick = isClosingClick(documentClick, {
            reference,
            floating,
            inside: closeOnInsideClick,
            outside: false,
        });

        const subscribers = [
            subscribeToUpdates(closingClick, (event: EventPredicateResult) =>
                dispatch("close", event),
            ),
        ];

        if (!keepOnKeyup) {
            const closingKeyup = isClosingKeyup(documentKeyup, {
                reference,
                floating,
            });

            subscribers.push(
                subscribeToUpdates(closingKeyup, (event: EventPredicateResult) =>
                    dispatch("close", event),
                ),
            );
        }

        autoAction = autoUpdate(reference, positioningCallback);
        cleanup = singleCallback(...subscribers, autoAction.destroy!);
    }

    $: updateFloating(reference, floating, show);
</script>

<slot {position} {asReference} />

{#if $$slots.reference}
    {#if inline}
        <span class="overlay-reference" use:asReference>
            <slot name="reference" />
        </span>
    {:else}
        <div class="overlay-reference" use:asReference>
            <slot name="reference" />
        </div>
    {/if}
{/if}

<div bind:this={floating} class="overlay" class:show>
    {#if show}
        <slot name="overlay" {position} />
    {/if}
</div>

<style lang="scss">
    .overlay {
        position: absolute;
        border-radius: var(--border-radius);

        z-index: 40;
    }
</style>
