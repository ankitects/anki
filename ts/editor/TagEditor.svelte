<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import StickyBottom from "components/StickyBottom.svelte";
    import AddTagBadge from "./AddTagBadge.svelte";
    import Tag from "./Tag.svelte";
    import TagInput from "./TagInput.svelte";

    export let tags = ["en::foobar", "test", "def"];

    let tagInputNew: HTMLInputElement;
    let newName: string = "";

    function focusInputNew(): void {
        tagInputNew.focus();
    }

    const insertTagAt = (index: number) => () => {
        const copy = tags.slice(0);
        copy.splice(index, 1);

        if (copy.includes(tags[index])) {
            return;
        }

        tags.splice(index - 1, 0, tags[index]);
        tags = tags;
    };

    const deleteTagAt = (index: number) => () => {
        tags.splice(index, 1);
        tags = tags;
    };

    function appendTag(): void {
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
            <Tag
                bind:name={tag}
                on:tagadd={insertTagAt(index)}
                on:tagdelete={deleteTagAt(index)}
            />
        {/each}

        <TagInput
            bind:input={tagInputNew}
            bind:name={newName}
            on:tagupdate={appendTag}
            on:tagadd={appendTag}
        />
    </div>
</StickyBottom>

<style lang="scss">
    div {
        font-size: 13px;
        fill: currentColor;
    }
</style>
