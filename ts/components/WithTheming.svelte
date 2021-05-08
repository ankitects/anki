<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    export let id: string | undefined = undefined;
    export let style: string;
</script>

<style lang="scss">
    div {
        display: contents;
    }
</style>

<div {id} {style}>
    <slot />
</div>
