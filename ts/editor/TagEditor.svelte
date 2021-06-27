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
    import TagInput from "./TagInput.svelte";
    import TagAutocomplete from "./TagAutocomplete.svelte";
    import ButtonToolbar from "components/ButtonToolbar.svelte";
    import type { Tag as TagType } from "./tags";
    import { attachId, getName } from "./tags";

    export let initialNames = ["en::foobar", "test", "def"];
    export let suggestions = ["en::idioms", "anki::functionality", "math"];

    export let size = isApplePlatform() ? 1.6 : 2.0;

    let input: HTMLInputElement;
    let tags = initialNames.map(attachId);

    function isFirst(index: number): boolean {
        return index === 0;
    }

    function isLast(index: number): boolean {
        return index === tags.length - 1;
    }

    let active: number | null = null;
    let activeAfterBlur: number | null = null;

    function setActiveAfterBlur(value: number): void {
        if (activeAfterBlur === null) {
            activeAfterBlur = value;
        }
    }

    function updateActiveAfterBlur(update: (value: number) => number | null): void {
        if (activeAfterBlur !== null) {
            activeAfterBlur = update(activeAfterBlur);
        }
    }

    function decideNextActive() {
        console.log("dna", active, activeAfterBlur, JSON.stringify(tags));
        active = activeAfterBlur;
        activeAfterBlur = null;
    }

    function insertEmptyTagAt(index: number): void {
        /* used by tag badge and tag spacer */
        tags.splice(index, 0, attachId(""));
        tags = tags;
        active = index;
    }

    function appendEmptyTagAt(index: number): void {
        tags.splice(index + 1, 0, attachId(""));
        tags = tags;
        setActiveAfterBlur(index + 1);
    }

    function checkIfUniqueNameAt(index: number): boolean {
        const names = tags.map(getName);
        const newName = names.splice(index, 1, "")[0];

        const contained = names.indexOf(newName);
        if (contained >= 0) {
            tags[contained].blink = true;
            return false;
        }

        return true;
    }

    function enterTag(_tag: TagType, index: number): void {
        appendEmptyTagAt(index);
    }

    function insertTag(tag: TagType, index: number): void {
        const name = tags.map(getName).splice(index, 1)[0];

        if (!checkIfUniqueNameAt(index)) {
            tags.splice(index, 0, attachId(name));
            tags = tags;
        }
    }

    function deleteTag(tag: TagType, index: number): TagType {
        const deleted = tags.splice(index, 1)[0];
        tags = tags;

        if (active !== null && active > index) {
            active--;
        }

        return deleted;
    }

    function deleteActiveTag(tag: TagType, index: number): TagType {
        const deleted = tags.splice(index, 1)[0];
        tags = tags;

        if (activeAfterBlur === index) {
            activeAfterBlur = null;
        } else if (activeAfterBlur !== null && activeAfterBlur > index) {
            activeAfterBlur--;
        }

        active = null;
        return deleted;
    }

    function deleteTagIfNotUnique(tag: TagType, index: number): void {
        if (!tags.includes(tag)) {
            // already deleted
            return;
        }

        if (!checkIfUniqueNameAt(index)) {
            deleteTag(tag, index);
        }
    }

    function joinWithPreviousTag(tag: TagType, index: number): void {
        if (isFirst(index)) {
            return;
        }

        const deleted = deleteTag(
            tag /* invalid, probably need to change signature of deleteTag */,
            index - 1
        );
        tag.name = deleted.name + tag.name;
        tags = tags;
        console.log(active, activeAfterBlur);
    }

    function joinWithNextTag(tag: TagType, index: number): void {
        if (isLast(index)) {
            return;
        }

        const deleted = deleteTag(tag, index + 1);
        tag.name = tag.name + deleted.name;
        tags = tags;
    }

    function moveToPreviousTag(tag: TagType, index: number): void {
        if (isFirst(index)) {
            return;
        }
        console.log("moveprev", index);
        active = null;
        activeAfterBlur = index - 1;
    }

    async function moveToNextTag(tag: TagType, index: number): Promise<void> {
        if (isLast(index)) {
            if (tag.name.length !== 0) {
                appendEmptyTagAt(index);
                active = null;
                activeAfterBlur = index + 1;
            }
            return;
        }

        active = null;
        activeAfterBlur = index + 1;

        await tick();
        input.setSelectionRange(0, 0);
    }

    function activate(index: number): void {
        active = index;
    }
</script>

<StickyBottom>
    <div class="row-gap">
        <ButtonToolbar class="dropup d-flex flex-wrap align-items-center" {size}>
            <AddTagBadge on:click={() => insertEmptyTagAt(tags.length)} />

            <TagAutocomplete
                class="d-flex flex-column-reverse"
                {suggestions}
                let:updateAutocomplete
                let:destroyAutocomplete
            >
                {#each tags as tag, index (tag.id)}
                    {#if index === active}
                        <TagInput
                            id={tag.id}
                            bind:name={tag.name}
                            bind:input
                            on:focus={() =>
                                console.log(
                                    "focused",
                                    tag,
                                    tag.name,
                                    JSON.stringify(tags)
                                )}
                            on:blur={decideNextActive}
                            on:tagenter={() => enterTag(tag, index)}
                            on:tagadd={() => insertTag(tag, index)}
                            on:tagdelete={() => deleteActiveTag(tag, index)}
                            on:tagunique={() => deleteTagIfNotUnique(tag, index)}
                            on:tagjoinprevious={() => joinWithPreviousTag(tag, index)}
                            on:tagjoinnext={() => joinWithNextTag(tag, index)}
                            on:tagmoveprevious={() => moveToPreviousTag(tag, index)}
                            on:tagmovenext={() => moveToNextTag(tag, index)}
                        />
                    {:else}
                        <Tag
                            name={tag.name}
                            bind:blink={tag.blink}
                            on:click={() => activate(index)}
                            on:tagdelete={() => deleteTag(tag, index)}
                        />
                    {/if}
                {/each}

                <div
                    class="tag-spacer flex-grow-1 align-self-stretch"
                    on:click={() => insertEmptyTagAt(tags.length)}
                />
            </TagAutocomplete>

            <div>{JSON.stringify(tags)}</div>
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
