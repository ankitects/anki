<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import ScrollArea from "./ScrollArea.svelte";

    export let scrollY = true;
</script>

<ScrollArea {scrollY} class="pane-content">
    <slot />
</ScrollArea>
