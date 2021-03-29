<script lang="typescript">
    import ButtonItem from "./ButtonItem.svelte";

    export let label: string;
</script>

<style lang="scss">
    button {
        display: inline-block;
        vertical-align: middle;
        width: auto;
        height: 30px;

        padding: 3px 10px;
        margin-right: 3px;

        &:focus {
            box-shadow: 0 0 0 0.05rem rgba(255, 255, 255, 0.5);
        }
    }
</style>

<ButtonItem>
    <button type="button" class="btn btn-secondary" on:click> {label} </button>
</ButtonItem>
