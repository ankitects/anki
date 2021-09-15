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

<footer {id} bind:offsetHeight={height} class={`container-fluid pt-1 ${className}`}>
    <slot />
</footer>

<style lang="scss">
    footer {
        position: sticky;
        bottom: 0;
        left: 0;
        right: 0;
        z-index: 10;

        background: var(--window-bg);
        border-top: 1px solid var(--medium-border);
    }
</style>
