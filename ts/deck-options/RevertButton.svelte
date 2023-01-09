<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "@tslib/ftl";
    import { cloneDeep, isEqual as isEqualLodash } from "lodash-es";
    import { getContext } from "svelte";

    import Badge from "../components/Badge.svelte";
    import { touchDeviceKey } from "../components/context-keys";
    import DropdownItem from "../components/DropdownItem.svelte";
    import Popover from "../components/Popover.svelte";
    import WithFloating from "../components/WithFloating.svelte";
    import { revertIcon } from "./icons";

    type T = unknown;

    export let value: T;
    export let defaultValue: T;

    function isEqual(a: T, b: T): boolean {
        if (typeof a === "number" && typeof b === "number") {
            // round to .01 precision before comparing,
            // so the values coming out of the UI match
            // the originals
            a = Math.round(a * 100) / 100;
            b = Math.round(b * 100) / 100;
        }

        return isEqualLodash(a, b);
    }

    let modified: boolean;
    $: modified = !isEqual(value, defaultValue);

    let showFloating = false;

    const isTouchDevice = getContext<boolean>(touchDeviceKey);

    function revert(): void {
        value = cloneDeep(defaultValue);
        showFloating = false;
    }
</script>

<WithFloating
    show={showFloating}
    closeOnInsideClick
    inline
    on:close={() => (showFloating = false)}
    let:asReference
>
    <div class:hide={!modified} use:asReference>
        <Badge
            iconSize={85}
            class="p-1"
            on:click={() => {
                if (modified) {
                    showFloating = !showFloating;
                }
            }}
        >
            {@html revertIcon}
        </Badge>
    </div>

    <Popover slot="floating">
        <DropdownItem
            class={`spinner ${isTouchDevice ? "spin-always" : ""}`}
            on:click={() => revert()}
        >
            {tr.deckConfigRevertButtonTooltip()}<Badge>{@html revertIcon}</Badge>
        </DropdownItem>
    </Popover>
</WithFloating>

<style lang="scss">
    :global(.spinner:hover .badge, .spinner.spin-always .badge) {
        animation: spin-animation 1s infinite;
        animation-timing-function: linear;
    }

    @keyframes -global-spin-animation {
        0% {
            transform: rotate(360deg);
        }
        100% {
            transform: rotate(0deg);
        }
    }

    :global(.badge) {
        cursor: pointer;
    }

    .hide :global(.badge) {
        opacity: 0;
        cursor: initial;
    }
</style>
