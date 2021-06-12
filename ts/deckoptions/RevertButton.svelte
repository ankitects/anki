<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "lib/i18n";
    import { getContext } from "svelte";
    import WithTooltip from "./WithTooltip.svelte";
    import Badge from "./Badge.svelte";
    import { revertIcon } from "./icons";
    import { touchDeviceKey } from "components/contextKeys";
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

    function revert(): void {
        value = cloneDeep(defaultValue);
    }

    const touchDevice = getContext<boolean>(touchDeviceKey);
</script>

<span class:invisible={!modified}>
    {#if touchDevice}
        <Badge class="px-1" on:click={revert}>{@html revertIcon}</Badge>
    {:else}
        <WithTooltip
            trigger="hover"
            tooltip={tr.deckConfigRevertButtonTooltip()}
            let:createTooltip
        >
            <Badge
                class="px-1"
                on:mount={(event) => createTooltip(event.detail.span)}
                on:click={revert}>{@html revertIcon}</Badge
            >
        </WithTooltip>
    {/if}
</span>
