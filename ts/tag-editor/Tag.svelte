<script lang="typescript">
    export let name: string;
</script>

<style lang="scss">
    .tag {
        /* important for tags with non-latin characters */
        line-height: 2ch;
    }

    $white-translucent: rgba(255, 255, 255, 0.5);

    .delete-icon:hover {
        background-color: $white-translucent;
    }
</style>

<span class="tag bg-secondary rounded p-1 me-2">
    <span>{name}</span>
    <span class="delete-icon bi bi-x rounded"></span>
</span>
