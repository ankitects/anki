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
    import { writable } from "svelte/store";
    import type { ActionReturn } from "svelte/action";

    import { singleCallback } from "../lib/typing";
    import type { Callback } from "../lib/typing"
    import isClosingClick from "../sveltelib/closing-click";
    import isClosingKeyup from "../sveltelib/closing-keyup";
    import { documentClick, documentKeyup } from "../sveltelib/event-store";
    import portal from "../sveltelib/portal";
    import type { PositioningCallback } from "../sveltelib/position/auto-update"
    import autoUpdate from "../sveltelib/position/auto-update"
    import type { PositionAlgorithm } from "../sveltelib/position/position-floating";
    import positionFloating from "../sveltelib/position/position-floating";
    import subscribeTrigger from "../sveltelib/subscribe-trigger";
    import { pageTheme } from "../sveltelib/theme";

    export let placement: Placement | "auto" = "bottom";
    export let offset = 5;
    export let shift = 5;

    let arrow: HTMLElement;

    $: positionCurried = positionFloating({
        placement,
        offset,
        shift,
        arrow,
    });

    let actionReturn: ActionReturn = {};

    $: {
        positionCurried;
        actionReturn.update?.(positioningCallback);
    }

    export let closeOnInsideClick = false;
    export let keepOnKeyup = false;

    /** This may be passed in for more fine-grained control */
    export let show = writable(true);

    export let reference: HTMLElement | undefined = undefined;
    let floating: FloatingElement;

    async function position(callback: (reference: HTMLElement, floating: FloatingElement, position: PositionAlgorithm) => void): Promise<void> {
        if (reference && floating) {
            return callback(reference, floating, positionCurried);
        }
    }

    function asReference(referenceArgument: HTMLElement) {
        reference = referenceArgument;
    }

    function positioningCallback(reference: HTMLElement, callback: PositioningCallback): Callback {
        return callback(reference, floating, () => positionCurried(reference, floating))
    }

    let cleanup: Callback;

    $: if (reference && floating) {
        cleanup?.();

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
</script>

{#if floating && arrow}
    <slot {position} {asReference} />
{/if}

<div bind:this={floating} class="floating" use:portal>
    {#if $show}
        <slot name="floating" />
    {/if}

    <div bind:this={arrow} hidden={!$show} class="arrow" class:dark={$pageTheme.isDark} />
</div>

<style lang="scss">
    @use "sass/elevation" as elevation;

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
        z-index: 60;

        /* outer border */
        border: 1px solid #b6b6b6;

        &.dark {
            border-color: #060606;
        }

        /* Rotate the box to indicate the different directions */
        border-right: none;
        border-bottom: none;

        /* inner border */
        box-shadow: inset 1px 1px 0 0 #eeeeee;

        &.dark {
            box-shadow: inset 1px 1px 0 0 #565656;
        }
    }
</style>
