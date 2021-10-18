<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    export let id: string | undefined = undefined;
    let className: string = "";
    export { className as class };

    export let height: number = 0;
</script>

<footer {id} bind:offsetHeight={height} class="container-fluid pt-1 {className}">
    <slot />
</footer>

<style lang="scss">
    footer {
        position: sticky;
        left: 0;
        right: 0;
        z-index: 10;

        background: var(--window-bg);
        border-style: solid;
        border-color: var(--medium-border);
        border-width: 0;
        padding: 0 3px;

        bottom: 0;
        border-top-width: 1px;
    }
</style>
