<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import StickyContainer from "../components/StickyContainer.svelte";

    export let heading: string;
</script>

<StickyContainer --sticky-border="var(--border)" --sticky-borders="0px 0 1px">
    <h1>
        {heading}
    </h1>
</StickyContainer>

<style lang="scss">
    h1 {
        padding-top: 0.5em;
    }
</style>
