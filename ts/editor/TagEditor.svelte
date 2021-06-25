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
    import ButtonToolbar from "components/ButtonToolbar.svelte";
    import TagInput from "./TagInput.svelte";
    import { attachId, getName } from "./tags";

    export let initialNames = ["en::foobar", "test", "def"];
    export let suggestions = ["en::idioms", "anki::functionality", "math"];

    export let size = isApplePlatform() ? 1.6 : 2.0;

    let tags = initialNames.map(attachId);
    let newInput: HTMLInputElement;
    let newName: string = "";

    function focusNewInput(): void {
        if (document.activeElement === newInput) {
            // refocus
            newInput.blur();
        }

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

    function moveToPreviousTag(index: number): void {
        if (index === 0 || tags.length === 1) {
            return;
        }

        const before = tags.splice(index - 1, 1)[0];
        tags.splice(index, 0, before);
        tags = tags;
    }

    function moveToNextTag(index: number): void {
        if (index === tags.length - 1 || tags.length === 1) {
            return;
        }
        // TODO
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

    function moveToLastTag(): void {
        appendTag();
    }
</script>

<StickyBottom>
    <div class="row-gap">
        <ButtonToolbar class="dropup d-flex flex-wrap align-items-center" {size}>
            <AddTagBadge on:click={focusNewInput} />

            <TagAutocomplete
                class="d-flex flex-column-reverse"
                {suggestions}
                let:updateAutocomplete
                let:destroyAutocomplete
            >
                {#each tags as tag, index (tag.id)}
                    <Tag
                        bind:name={tag.name}
                        on:keydown={updateAutocomplete}
                        on:blur={destroyAutocomplete}
                        on:tagupdate={() => checkForDuplicateAt(index)}
                        on:tagadd={() => insertTagAt(index)}
                        on:tagdelete={() => deleteTagAt(index)}
                        on:tagjoinprevious={() => joinWithPreviousTag(index)}
                        on:tagjoinnext={() => joinWithNextTag(index)}
                        on:tagmoveprevious={() => moveToPreviousTag(index)}
                        on:tagmovenext={() => moveToNextTag(index)}
                    />
                {/each}

                <TagInput
                    bind:input={newInput}
                    bind:name={newName}
                    on:keydown={updateAutocomplete}
                    on:blur={destroyAutocomplete}
                    on:tagupdate={appendTag}
                    on:tagadd={appendTag}
                    on:tagjoinprevious={joinWithLastTag}
                    on:tagmoveprevious={moveToLastTag}
                />
            </TagAutocomplete>
        </ButtonToolbar>
    </div>
</StickyBottom>

<style lang="scss">
    .row-gap > :global(.d-flex > *) {
        margin-bottom: 2px;
    }
</style>
