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
    import { createEventDispatcher, onDestroy, setContext } from "svelte";
    import type { ActionReturn } from "svelte/action";
    import { writable } from "svelte/store";

    import isClosingClick from "../sveltelib/closing-click";
    import isClosingKeyup from "../sveltelib/closing-keyup";
    import type { EventPredicateResult } from "../sveltelib/event-predicate";
    import { documentClick, documentKeyup } from "../sveltelib/event-store";
    import portal from "../sveltelib/portal";
    import type { PositioningCallback } from "../sveltelib/position/auto-update";
    import autoUpdate from "../sveltelib/position/auto-update";
    import type { PositionAlgorithm } from "../sveltelib/position/position-algorithm";
    import positionFloating from "../sveltelib/position/position-floating";
    import subscribeToUpdates from "../sveltelib/subscribe-updates";
    import { floatingKey } from "./context-keys";
    import FloatingArrow from "./FloatingArrow.svelte";

    export let portalTarget: HTMLElement | null = null;

    let placement: Placement = "bottom";
    export { placement as preferredPlacement };

    /* Used by Popover to set animation direction depending on placement */
    const placementPromise = writable(undefined as Promise<Placement> | undefined);
    setContext(floatingKey, placementPromise);

    export let offset = 5;
    /* 30px box shadow from elevation(8) */
    export let shift = 30;
    export let inline = false;
    export let hideIfEscaped = false;
    export let hideIfReferenceHidden = false;

    /** This may be passed in for more fine-grained control */
    export let show = true;

    const dispatch = createEventDispatcher();

    let arrow: HTMLElement;

    $: positionCurried = positionFloating({
        placement,
        offset,
        shift,
        inline,
        arrow,
        hideIfEscaped,
        hideIfReferenceHidden,
        hideCallback: (reason: string) => dispatch("close", { reason }),
    });

    let autoAction: ActionReturn = {};

    $: {
        positionCurried;
        autoAction.update?.(positioningCallback);
    }

    export let closeOnInsideClick = false;
    export let keepOnKeyup = false;
    export let hideArrow = false;

    export let reference: ReferenceElement | undefined = undefined;
    let floating: FloatingElement;

    function applyPosition(
        reference: ReferenceElement,
        floating: FloatingElement,
        position: PositionAlgorithm,
    ): Promise<Placement> {
        const promise = position(reference, floating);
        $placementPromise = promise;
        return promise;
    }

    async function position(
        callback: (
            reference: ReferenceElement,
            floating: FloatingElement,
            position: PositionAlgorithm,
        ) => Promise<Placement> = applyPosition,
    ): Promise<Placement | void> {
        if (reference && floating) {
            return callback(reference, floating, positionCurried);
        }
    }

    function asReference(referenceArgument: Element) {
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

    let cleanup: Callback | null = null;

    function updateFloating(
        reference: ReferenceElement | undefined,
        floating: FloatingElement,
        isShowing: boolean,
    ) {
        cleanup?.();
        cleanup = null;

        if (!reference || !floating || !isShowing) {
            return;
        }

        autoAction = autoUpdate(reference, positioningCallback);

        // For virtual references, we cannot provide any
        // default closing behavior
        if (!(reference instanceof EventTarget)) {
            cleanup = autoAction.destroy!;
            return;
        }

        const closingClick = isClosingClick(documentClick, {
            reference,
            floating,
            inside: closeOnInsideClick,
            outside: true,
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

<div bind:this={floating} class="floating" class:show use:portal={portalTarget}>
    {#if show}
        <slot name="floating" />
    {/if}

    <div bind:this={arrow} class="floating-arrow" hidden={!show}>
        {#if !hideArrow}
            <FloatingArrow />
        {/if}
    </div>
</div>

<style lang="scss">
    span.floating-reference {
        line-height: 1;
    }
    .floating {
        position: absolute;
        border-radius: 5px;

        z-index: 890;

        &-arrow {
            position: absolute;
        }
    }
</style>
