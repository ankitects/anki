<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import StickyBottom from "components/StickyBottom.svelte";
    import Badge from "components/Badge.svelte";
    import Tag from "./Tag.svelte";
    import { tagIcon } from "./icons";
</script>

<StickyBottom>
    <div>
        <Badge class="me-1">{@html tagIcon}</Badge>
        <Tag>en::foobar</Tag>
        <Tag>zh::あちこ</Tag>
    </div>
</StickyBottom>

<style lang="scss">
    div {
        display: flex;
        fill: currentColor;

        :global(#mdi-tag-outline) {
            height: 100%;
        }
    }
</style>
