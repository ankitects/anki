<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    export let id: string | undefined = undefined;
    let className: string = "";
    export { className as class };
    export let height: number;
</script>

<nav {id} bind:offsetHeight={height} class={`container-fluid pb-1 pt-1 ${className}`}>
    <slot />
</nav>

<style lang="scss">
    nav {
        position: fixed;
        top: 0;
        left: 0;
        right: 0;
        z-index: 10;

        background: var(--window-bg);
        border-bottom: 1px solid var(--medium-border);
    }
</style>
