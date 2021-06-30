<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "lib/i18n";
    import type Dropdown from "bootstrap/js/dist/dropdown";
    import WithDropdown from "components/WithDropdown.svelte";
    import DropdownMenu from "components/DropdownMenu.svelte";
    import DropdownItem from "components/DropdownItem.svelte";
    import Badge from "./Badge.svelte";
    import { revertIcon } from "./icons";
    import { isEqual as isEqualLodash, cloneDeep } from "lodash-es";
    import { touchDeviceKey } from "components/context-keys";
    import { getContext } from "svelte";

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

    let dropdown: Dropdown;

    const isTouchDevice = getContext<boolean>(touchDeviceKey);

    function revert(): void {
        value = cloneDeep(defaultValue);
        dropdown.hide();
    }
</script>

<WithDropdown let:createDropdown>
    <div class:hide={!modified}>
        <Badge
            class="p-1"
            on:mount={(event) => (dropdown = createDropdown(event.detail.span))}
            on:click={() => {
                if (modified) {
                    dropdown.toggle();
                }
            }}
        >
            {@html revertIcon}
        </Badge>

        <DropdownMenu>
            <DropdownItem
                class={`spinner ${isTouchDevice ? "spin-always" : ""}`}
                on:click={() => revert()}
            >
                {tr.deckConfigRevertButtonTooltip()}<Badge>{@html revertIcon}</Badge>
            </DropdownItem>
        </DropdownMenu>
    </div>
</WithDropdown>

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

    .hide :global(.badge) {
        opacity: 0;
    }
</style>
