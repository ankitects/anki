<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import StickyBottom from "components/StickyBottom.svelte";
    import AddTagBadge from "./AddTagBadge.svelte";
    import Tag from "./Tag.svelte";
    import TagInput from "./TagInput.svelte";
    import { attachId, getName } from "./tags";

    export let initialNames = ["en::foobar", "test", "def"];

    let tags = initialNames.map(attachId);
    let newInput: HTMLInputElement;
    let newName: string = "";

    function focusNewInput(): void {
        newInput.focus();
    }

    function checkForDuplicateAt(index: number): void {
        const names = tags.map(getName);
        const nameToUpdateTo = names.splice(index, 1)[0];

        if (names.includes(nameToUpdateTo)) {
            deleteTagAt(index);
        }
    }

    function insertTagAt(index: number): void {
        const names = tags.map(getName);
        const nameToInsert = names.splice(index, 1)[0];

        if (names.includes(nameToInsert)) {
            return;
        }

        tags.splice(index, 0, attachId(nameToInsert));
        tags = tags;
    }

    function deleteTagAt(index: number): void {
        tags.splice(index, 1);
        tags = tags;
    }

    function appendTag(): void {
        const names = tags.map(getName);
        if (!names.includes(newName) && newName.length > 0) {
            tags.push(attachId(newName));
            tags = tags;
        }

        newName = "";
    }
</script>

<StickyBottom>
    <div class="d-flex flex-wrap">
        <AddTagBadge on:click={focusNewInput} />

        {#each tags as tag, index (tag.id)}
            <Tag
                bind:name={tag.name}
                on:tagupdate={() => checkForDuplicateAt(index)}
                on:tagadd={() => insertTagAt(index)}
                on:tagdelete={() => deleteTagAt(index)}
            />
        {/each}

        <TagInput
            bind:input={newInput}
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
