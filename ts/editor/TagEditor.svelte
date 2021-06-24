<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import StickyBottom from "components/StickyBottom.svelte";
    import AddTagBadge from "./AddTagBadge.svelte";
    import Tag from "./Tag.svelte";
    import TagInput from "./TagInput.svelte";

    export let tags = ["en::foobar", "zh::あっちこっち", "test", "def"];

    let tagInputNew: HTMLInputElement;
    let newName: string = "";

    function focusInputNew(): void {
        tagInputNew.focus();
    }

    function deleteTag(index: number): void {
        tags.splice(index, 1);
        tags = tags;
    }

    function addTag(): void {
        if (!tags.includes(newName) && newName.length > 0) {
            tags.push(newName);
        }
        newName = "";
        tags = tags;
    }
</script>

<StickyBottom>
    <div class="d-flex flex-wrap">
        <AddTagBadge on:click={focusInputNew} />

        {#each tags as tag, index}
            <Tag bind:name={tag} on:tagdelete={() => deleteTag(index)} />
        {/each}

        <TagInput bind:input={tagInputNew} bind:name={newName} on:tagupdate={addTag} />
    </div>
</StickyBottom>

<style lang="scss">
    div {
        font-size: 13px;
        fill: currentColor;
    }
</style>
