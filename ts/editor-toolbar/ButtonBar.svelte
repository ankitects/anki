<style lang="scss">
    ul {
        padding-left: 0;
        margin-bottom: 2px;

        & > :global(*) {
            margin: 0 4px;

            &:first-child {
                margin-left: 0;
            }

            &:last-child {
                margin-right: 0;
            }
        }
    }
</style>

<ul><slot/></ul>
