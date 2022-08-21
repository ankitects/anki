<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import type { FloatingElement } from "@floating-ui/dom";
    import { createEventDispatcher } from "svelte";
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
    import positionOverlay from "../sveltelib/position/position-overlay";
    import subscribeToUpdates from "../sveltelib/subscribe-updates";

    export let padding = 0;
    export let inline = false;

    /** This may be passed in for more fine-grained control */
    export let show = true;

    const dispatch = createEventDispatcher();

    $: positionCurried = positionOverlay({
        padding,
        inline,
        hideCallback: (reason: symbol) => dispatch("close", reason),
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
            subscribeToUpdates(closingClick, (reason: symbol): void => {
                dispatch("close", reason);
            }),
        ];

        if (!keepOnKeyup) {
            const closingKeyup = isClosingKeyup(documentKeyup, {
                reference,
                floating,
            });

            subscribers.push(
                subscribeToUpdates(closingKeyup, (reason: symbol): void => {
                    dispatch("close", reason);
                }),
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

<div bind:this={floating} class="overlay" use:portal>
    {#if show}
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
