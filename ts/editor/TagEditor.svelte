<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import StickyBottom from "components/StickyBottom.svelte";
    import Badge from "components/Badge.svelte";
    import Tag from "./Tag.svelte";
    import TagInputNew from "./TagInputNew.svelte";
    import { tagIcon } from "./icons";

    export let tags = ["en::foobar", "zh::あっちこっち"];

    let tagInputNew: HTMLInputElement;
    let inputNew = false;

    function focusTagInputNew(): void {
        inputNew = true;
        tagInputNew.focus();
    }

    function deleteTag({ detail }: CustomEvent) {
        tags.splice(tags.indexOf(detail.name), 1);
        tags = tags;
    }
</script>

<StickyBottom>
    <div class="d-flex flex-wrap" on:click={focusTagInputNew}>
        <Badge class="me-1">{@html tagIcon}</Badge>

        {#each tags as tag}
            <Tag name={tag} on:tagdelete={deleteTag} />
        {/each}

        {#if inputNew}
            <TagInputNew bind:input={tagInputNew} on:blur={() => (inputNew = false)} />
        {/if}
    </div>
</StickyBottom>

<style lang="scss">
    :global(#mdi-tag-outline) {
        fill: currentColor;
        height: 100%;
    }
</style>
