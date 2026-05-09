<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "@generated/ftl";
    import { cloneDeep, isEqual as isEqualLodash } from "lodash-es";

    import { revertIcon } from "$lib/components/icons";

    import Badge from "./Badge.svelte";
    import DropdownItem from "./DropdownItem.svelte";
    import Icon from "./Icon.svelte";
    import Popover from "./Popover.svelte";
    import WithFloating from "./WithFloating.svelte";

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
            <Icon icon={revertIcon} />
        </Badge>
    </div>

    <Popover slot="floating">
        <DropdownItem on:click={() => revert()}>
            {tr.deckConfigRevertButtonTooltip()}
        </DropdownItem>
    </Popover>
</WithFloating>

<style lang="scss">
    :global(.badge) {
        cursor: pointer;
    }

    .hide :global(.badge) {
        display: none;
        cursor: initial;
    }
</style>
