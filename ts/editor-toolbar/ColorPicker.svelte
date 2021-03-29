<script lang="typescript">
    export let className: string;
    export let onChange: (event: ChangeEvent) => void;
</script>

<style lang="scss">
    button {
        display: inline-block;
        vertical-align: middle;
        width: 30px;
        height: 30px;

        padding: 0;
    }

    span {
        display: inline-block;
        width: 100%;
        height: 100%;
    }

    input {
        opacity: 0;
        width: 100%;
        height: 100%;
    }
</style>

<button>
    <span class={className}> <input type="color" on:change={onChange} /> </span>
</button>
