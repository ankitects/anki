<script lang="typescript">
    export let className: string;
    export let onChange: (event: ChangeEvent) => void;
</script>

<style lang="scss">
    button {
        display: inline-block;
        vertical-align: middle;
        width: 28px;
        height: 28px;

        padding: 0;

        &:hover {
            background-color: #eee;
        }

        &:active {
            box-shadow: inset 0 0 12px 4px rgb(0 0 0 / 30%);
            border-color: #aaa;
        }
    }

    span {
        display: inline-block;
        width: 100%;
        height: 100%;
        cursor: pointer;
    }

    input {
        opacity: 0;
        width: 100%;
        height: 100%;
        cursor: pointer;
    }
</style>

<button tabindex="-1" on:mousedown|preventDefault> 
    <span class={className}> <input type="color" on:change={onChange} /> </span>
</button>
