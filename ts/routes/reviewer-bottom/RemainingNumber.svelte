<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    export let underlined: boolean;
    export let cls: string;
</script>

<span class={cls}>
    {#if underlined}
        <u><slot /></u>
    {:else}
        <slot />
    {/if}
</span>
