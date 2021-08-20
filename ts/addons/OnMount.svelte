<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script>
    export let component;
    export let handler;
</script>

<div class="contents" on:mount={handler}>
    <svelte:component this={component.component} {...component.props} />
</div>

<style>
    div {
        display: contents;
    }
</style>
