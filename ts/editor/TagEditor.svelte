<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import { isApplePlatform } from "lib/platform";
    import StickyBottom from "components/StickyBottom.svelte";
    import AddTagBadge from "./AddTagBadge.svelte";
    import Tag from "./Tag.svelte";
    import TagAutocomplete from "./TagAutocomplete.svelte";
    import TagInput from "./TagInput.svelte";
    import { attachId, getName } from "./tags";

    export let initialNames = ["en::foobar", "test", "def"];
    export let suggestions = ["en::idioms", "anki::functionality", "math"];

    export let size = isApplePlatform() ? 1.6 : 2.0;

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

    function joinWithPreviousTag(index: number): void {
        if (index === 0) {
            return;
        }

        const spliced = tags.splice(index - 1, 1)[0];
        tags[index - 1].name = spliced.name + tags[index - 1].name;
        tags = tags;
    }

    function joinWithNextTag(index: number): void {
        if (index === tags.length - 1) {
            return;
        }

        const spliced = tags.splice(index + 1, 1)[0];
        tags[index].name = tags[index].name + spliced.name;
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

    function joinWithLastTag(): void {
        const popped = tags.pop();
        tags = tags;

        if (popped) {
            newName = popped.name + newName;
        }
    }
</script>

<StickyBottom>
    <div class="row-gap">
        <TagAutocomplete
            class="d-flex flex-wrap align-items-center"
            {suggestions}
            {size}
            let:createDropdown
            let:activateDropdown
        >
            <AddTagBadge on:click={focusNewInput} />

            {#each tags as tag, index (tag.id)}
                <Tag
                    bind:name={tag.name}
                    on:tagupdate={() => checkForDuplicateAt(index)}
                    on:tagadd={() => insertTagAt(index)}
                    on:tagdelete={() => deleteTagAt(index)}
                    on:tagjoinprevious={() => joinWithPreviousTag(index)}
                    on:tagjoinnext={() => joinWithNextTag(index)}
                />
            {/each}

            <TagInput
                bind:input={newInput}
                bind:name={newName}
                on:focus={(event) => createDropdown(event.currentTarget)}
                on:tagupdate={appendTag}
                on:tagadd={appendTag}
                on:tagjoinprevious={joinWithLastTag}
            />
        </TagAutocomplete>
    </div>
</StickyBottom>

<style lang="scss">
    .row-gap > :global(.d-flex > *) {
        margin-bottom: 2px;
    }
</style>
