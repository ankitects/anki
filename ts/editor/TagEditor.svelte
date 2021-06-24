<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import StickyBottom from "components/StickyBottom.svelte";
    import AddTagBadge from "./AddTagBadge.svelte";
    import Tag from "./Tag.svelte";
    import TagInput from "./TagInput.svelte";

    export let tags = ["en::foobar", "zh::あっちこっち"];

    let tagInputNew: HTMLInputElement;
    let newName: string = "";

    function focusInputNew(): void {
        tagInputNew.focus();
    }

    function deleteTag({ detail }: CustomEvent) {
        tags.splice(tags.indexOf(detail.name), 1);
        tags = tags;
    }

    function addTag() {
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

        {#each tags as tag}
            <Tag name={tag} on:tagdelete={deleteTag} />
        {/each}

        <TagInput bind:input={tagInputNew} name={newName} on:tagupdate={addTag} />
    </div>
</StickyBottom>

<style lang="scss">
    div {
        font-size: 13px;
        fill: currentColor;
    }
</style>
