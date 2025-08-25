<script lang="ts">
    export let underlined: boolean
    export let cls: string
</script>

<span class={cls}>
    {#if underlined}
        <u><slot/></u>
    {:else}
        <slot/>
    {/if}
</span>
