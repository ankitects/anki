<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    export let underlined: boolean;
    export let cls: string;
    export let count: number | undefined;

    $: displayCount = count?.toFixed(0) ?? "?";
</script>

<span class={cls}>
    {#if underlined}
        <u>{displayCount}</u>
    {:else}
        {displayCount}
    {/if}
</span>
