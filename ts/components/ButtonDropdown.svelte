<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import { setContext } from "svelte";
    import { dropdownKey } from "./contextKeys";

    import WithTheming from "./WithTheming.svelte";
    import ButtonToolbar from "./ButtonToolbar.svelte";

    export let id: string | undefined;
    let className = "";
    export { className as class };

    export let api: Record<string, unknown> | undefined = undefined;

    setContext(dropdownKey, null);
</script>

<style lang="scss">
    :global(.dropdown-menu.btn-dropdown-menu) {
        display: none;
        min-width: 0;
        padding: calc(var(--toolbar-size) / 10) 0;

        background-color: var(--window-bg);
        border-color: var(--medium-border);

        :global(.btn-group) {
            position: static;
        }
    }

    :global(.dropdown-menu.btn-dropdown-menu.show) {
        display: flex;
    }
</style>

<WithTheming style="--toolbar-wrap: nowrap; ">
    <ButtonToolbar
        {id}
        class={`dropdown-menu btn-dropdown-menu ${className}`}
        nowrap={true}
        {api}>
        <slot />
    </ButtonToolbar>
</WithTheming>
