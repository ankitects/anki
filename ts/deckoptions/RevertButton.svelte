<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "lib/i18n";
    import WithDropdownMenu from "components/WithDropdownMenu.svelte";
    import DropdownMenu from "components/DropdownMenu.svelte";
    import DropdownItem from "components/DropdownItem.svelte";
    import Badge from "./Badge.svelte";
    import { gearIcon, revertIcon } from "./icons";
    import { isEqual as isEqualLodash, cloneDeep } from "lodash-es";

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
    $: className = !modified ? "opacity-25" : "";

    function revert(): void {
        value = cloneDeep(defaultValue);
    }
</script>

<WithDropdownMenu let:createDropdown let:menuId>
    <Badge class={className} on:mount={(event) => createDropdown(event.detail.span)}>
        {@html gearIcon}
    </Badge>

    <DropdownMenu id={menuId}>
        <DropdownItem on:click={revert}>
            {tr.deckConfigRevertButtonTooltip()}<Badge>{@html revertIcon}</Badge>
        </DropdownItem>
    </DropdownMenu>
</WithDropdownMenu>
