<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import { tick } from "svelte";
    import { isApplePlatform } from "lib/platform";
    import StickyBottom from "components/StickyBottom.svelte";
    import AddTagBadge from "./AddTagBadge.svelte";
    import Tag from "./Tag.svelte";
    import TagAutocomplete from "./TagAutocomplete.svelte";
    import ButtonToolbar from "components/ButtonToolbar.svelte";
    import { attachId, getName } from "./tags";

    export let initialNames = ["en::foobar", "test", "def"];
    export let suggestions = ["en::idioms", "anki::functionality", "math"];

    export let size = isApplePlatform() ? 1.6 : 2.0;

    let tags = initialNames.map((name) => attachId(name));

    function isFirst(index: number): boolean {
        return index === 0;
    }

    function isLast(index: number): boolean {
        return index === tags.length - 1;
    }

    function addEmptyTag(): void {
        if (tags[tags.length - 1].name.length === 0) {
            tags[tags.length - 1].active = true;
            return;
        }

        tags.push(attachId("", true));
        tags = tags;
    }

    function insertEmptyTagAt(index: number): void {
        tags.splice(index, 0, attachId("", true));
        tags = tags;
    }

    function appendEmptyTagAt(index: number): void {
        tags.splice(index + 1, 0, attachId("", true));
        tags = tags;
    }

    function checkIfContainsNameAt(index: number): boolean {
        const names = tags.map(getName);
        const newName = names.splice(index, 1, "")[0];

        const contained = names.indexOf(newName);
        if (contained >= 0) {
            tags[contained].blink = true;
            return true;
        }

        return false;
    }

    function addTagAt(index: number): void {
        if (checkIfContainsNameAt(index)) {
            deleteTagAt(index);
            insertEmptyTagAt(index);
        } else {
            appendEmptyTagAt(index);
        }
    }

    function insertTagAt(index: number): void {
        const name = tags.map(getName).splice(index, 1)[0];

        if (!checkIfContainsNameAt(index)) {
            tags.splice(index, 0, attachId(name));
            tags = tags;
        }
    }

    function deleteTagAt(index: number): void {
        tags.splice(index, 1);
        tags = tags;
    }

    function joinWithPreviousTag(index: number): void {
        if (isFirst(index)) {
            return;
        }

        const spliced = tags.splice(index - 1, 1)[0];
        tags[index - 1].name = spliced.name + tags[index - 1].name;
        tags = tags;
    }

    function joinWithNextTag(index: number): void {
        if (isLast(index)) {
            return;
        }

        const spliced = tags.splice(index + 1, 1)[0];
        tags[index].name = tags[index].name + spliced.name;
        tags = tags;
    }

    function moveToPreviousTag(index: number): void {
        if (isFirst(index)) {
            return;
        }

        const previousTag = tags[index - 1];
        previousTag.active = true;
        tags = tags;
    }

    async function moveToNextTag(index: number): Promise<void> {
        if (isLast(index)) {
            addEmptyTag();
            return;
        }

        const nextTag = tags[index + 1];
        nextTag.active = true;
        tags = tags;

        await tick();
        nextTag.input?.setSelectionRange(0, 0);
    }
</script>

<StickyBottom>
    <div class="row-gap">
        <ButtonToolbar class="dropup d-flex flex-wrap align-items-center" {size}>
            <AddTagBadge on:click={addEmptyTag} />

            <TagAutocomplete
                class="d-flex flex-column-reverse"
                {suggestions}
                let:updateAutocomplete
                let:destroyAutocomplete
            >
                {#each tags as tag, index (tag.id)}
                    <Tag
                        bind:name={tag.name}
                        bind:input={tag.input}
                        bind:active={tag.active}
                        bind:blink={tag.blink}
                        on:tagupdate={() => addTagAt(index)}
                        on:tagadd={() => insertTagAt(index)}
                        on:tagdelete={() => deleteTagAt(index)}
                        on:tagjoinprevious={() => joinWithPreviousTag(index)}
                        on:tagjoinnext={() => joinWithNextTag(index)}
                        on:tagmoveprevious={() => moveToPreviousTag(index)}
                        on:tagmovenext={() => moveToNextTag(index)}
                    />
                {/each}

                <div
                    class="tag-spacer flex-grow-1 align-self-stretch"
                    on:click={addEmptyTag}
                />
            </TagAutocomplete>
        </ButtonToolbar>
    </div>
</StickyBottom>

<style lang="scss">
    .row-gap > :global(.d-flex > *) {
        margin-bottom: 2px;
    }

    .tag-spacer {
        cursor: text;
    }
</style>
