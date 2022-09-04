<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import type { Placement } from "@floating-ui/dom";
    import { onMount } from "svelte";
    import { writable } from "svelte/store";

    import isClosingClick from "../sveltelib/closing-click";
    import isClosingKeyup from "../sveltelib/closing-keyup";
    import { documentClick, documentKeyup } from "../sveltelib/event-store";
    import portal from "../sveltelib/portal";
    import type { PositionArgs } from "../sveltelib/position";
    import position from "../sveltelib/position";
    import subscribeTrigger from "../sveltelib/subscribe-trigger";
    import { pageTheme } from "../sveltelib/theme";
    import toggleable from "../sveltelib/toggleable";

    export let placement: Placement = "bottom";
    export let closeOnInsideClick = false;
    export let keepOnKeyup = false;

    /** This may be passed in for more fine-grained control */
    export let show = writable(false);

    let reference: HTMLElement;
    let floating: HTMLElement;
    let arrow: HTMLElement;

    const { toggle, on, off } = toggleable(show);

    let args: PositionArgs;
    $: args = {
        floating: $show ? floating : null,
        placement,
        arrow,
    };

    let update: (args: PositionArgs) => void;
    $: update?.(args);

    function asReference(element: HTMLElement) {
        const pos = position(element, args);
        reference = element;
        update = pos.update;

        return {
            destroy() {
                pos.destroy();
            },
        };
    }

    onMount(() => {
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

        subscribeTrigger(show, ...triggers);
    });
</script>

<slot name="reference" {show} {toggle} {on} {off} {asReference} />

<div bind:this={floating} class="floating" hidden={!$show} use:portal>
    <slot name="floating" />
    <div bind:this={arrow} class="arrow" class:dark={$pageTheme.isDark} />
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
        background-color: var(--canvas-elevated);
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
