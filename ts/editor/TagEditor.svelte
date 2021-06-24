<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import StickyBottom from "components/StickyBottom.svelte";
    import Badge from "components/Badge.svelte";
    import Tag from "./Tag.svelte";
    import TagInputNew from "./TagInputNew.svelte";
    import { addTagIcon } from "./icons";

    export let tags = ["en::foobar", "zh::あっちこっち"];

    let tagInputNew: HTMLInputElement;
    let inputNew = false;

    function focusInputNew(): void {
        inputNew = true;
        tagInputNew.focus();
    }

    function deleteTag({ detail }: CustomEvent) {
        tags.splice(tags.indexOf(detail.name), 1);
        tags = tags;
    }
</script>

<StickyBottom>
    <div class="d-flex flex-wrap">
        <Badge class="add-icon d-flex me-1" on:click={focusInputNew}
            >{@html addTagIcon}</Badge
        >

        {#each tags as tag}
            <Tag name={tag} on:tagdelete={deleteTag} />
        {/each}

        <div d-none={!inputNew}>
            <TagInputNew bind:input={tagInputNew} on:blur={() => (inputNew = false)} />
        </div>
    </div>
</StickyBottom>

<style lang="scss">
    div {
        font-size: 13px;
        fill: currentColor;

        :global(.add-icon > svg) {
            cursor: pointer;
            opacity: 0.5;
        }

        :global(.add-icon > svg:hover) {
            opacity: 1;
        }
    }
</style>
