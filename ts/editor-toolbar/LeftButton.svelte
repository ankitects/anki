<script lang="typescript">
    import ButtonItem from "./ButtonItem.svelte";
</script>
<style lang="scss">
    button {
        padding: 3px 10px;
        /* fit RightButton */
        vertical-align: -.425em;
    }

    button:focus {
        0 0 0 0.05rem rgba(255, 255, 255, 0.5);
    }
</style>

<ButtonItem>
    <button type="button" class="btn btn-secondary" on:click>
        <slot />
    </button>
</ButtonItem>
