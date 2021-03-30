<script lang="typescript">
    export let label: string;
</script>

<style lang="scss">
    button {
        display: inline-block;
        vertical-align: middle;
        width: auto;
        height: 26px;

        padding: 0 10px;
        margin: 0 3px;

        &:focus {
            box-shadow: 0 0 0 0.05rem rgba(255, 255, 255, 0.5);
        }
    }
</style>

<button type="button" class="btn btn-secondary" on:click> {label} </button>
