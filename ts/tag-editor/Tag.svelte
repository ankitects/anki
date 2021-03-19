<script lang="typescript">
    export let name: string;
</script>

<style lang="scss">
    .tag {
        /* important for tags with non-latin characters */
        line-height: 2ch;
    }
</style>

<span class="tag text-nowrap bg-secondary rounded p-1 me-2" on:click>
    <span>{name}</span>
    <slot name="after" />
</span>
