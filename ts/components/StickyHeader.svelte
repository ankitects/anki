<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    export let id: string | undefined = undefined;
    let className: string = "";
    export { className as class };
</script>

<header {id} class="container-fluid pb-1 {className}">
    <slot />
</header>

<style lang="scss">
    header {
        position: sticky;
        top: 0;
        left: 0;
        right: 0;
        z-index: 10;

        background: var(--window-bg);
        border-bottom: 1px solid var(--medium-border);
    }
</style>
