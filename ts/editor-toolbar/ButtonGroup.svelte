<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import { setContext, getContext } from "svelte";
    import { nightModeKey, buttonGroupKey } from "./contextKeys";

    export let id: string | undefined = undefined;
    let className = "";

    export { className as class };
    const nightMode = getContext(nightModeKey);

    export let api = {};

    let index = 0;

    interface ButtonRegistration {
        order: number;
    }

    function registerButton(): ButtonRegistration {
        index++;
        return { order: index };
    }

    setContext(buttonGroupKey, Object.assign(api, {
        registerButton,
    }))
</script>

<style lang="scss">
    div {
        display: flex;
        justify-items: start;

        flex-wrap: var(--toolbar-wrap);

        padding: calc(var(--toolbar-size) / 10);
        margin: 0;

        > :global(button),
        > :global(select) {
            border-radius: calc(var(--toolbar-size) / 7.5);

            &:not(:nth-of-type(1)) {
                border-top-left-radius: 0;
                border-bottom-left-radius: 0;
            }

            &:not(:nth-last-of-type(1)) {
                border-top-right-radius: 0;
                border-bottom-right-radius: 0;
            }
        }

        &.border-overlap-group {
            :global(button),
            :global(select) {
                margin-left: -1px;
            }
        }

        &.gap-group {
            :global(button),
            :global(select) {
                margin-left: 1px;
            }
        }
    }
</style>

<div
    {id}
    class={className}
    class:border-overlap-group={!nightMode}
    class:gap-group={nightMode}
    div="ltr">
    <slot />
</div>
