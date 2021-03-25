<style lang="scss">
    div {
        display: flex;
        flex-wrap: wrap;
        justify-content: space-between;

        position: sticky;
        top: 0;
        left: 0;
        z-index: 5;
        padding: 2px;

        background: var(--bg-color);
    }
</style>

<div><slot /></div>
