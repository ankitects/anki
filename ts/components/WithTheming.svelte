<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    export let id: string | undefined = undefined;
    let className = "";
    export { className as class };
    export let style: string;
</script>

<div {id} class={className} {style}>
    <slot />
</div>

<style lang="scss">
    div {
        display: contents;
    }
</style>
