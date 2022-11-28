<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import ScrollArea from "./ScrollArea.svelte";

    let className: string = "";
    export { className as class };
</script>

<div class="page {className} d-flex flex-column">
    <slot name="header" />

    <ScrollArea scrollY>
        <div class="page-content flex-grow-1">
            <slot />
        </div>
    </ScrollArea>

    <slot name="footer" />
</div>

<style lang="scss">
    .page {
        width: 100vw;
        height: 100vh;
    }
</style>
