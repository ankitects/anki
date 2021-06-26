<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
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

    async function addEmptyTag(): Promise<void> {
        const lastTag = tags[tags.length - 1];

        if (lastTag.name.length === 0) {
            return;
        }

        const index = tags.push(attachId("")) - 1;
        tags = tags;
        active = index;
    }

    function insertEmptyTagAt(index: number): void {
        tags.splice(index, 0, attachId(""));
        tags = tags;
        setActiveAfterBlur(index);
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

    function enterTag(tag: TagType, index: number): void {
        appendEmptyTagAt(index);
    }

    function insertTag(tag: TagType, index: number): void {
        const name = tags.map(getName).splice(index, 1)[0];

        if (!checkIfUniqueNameAt(index)) {
            tags.splice(index, 0, attachId(name));
            tags = tags;
        }
    }

    function deleteTag(tag: TagType, index: number): void {
        tags.splice(index, 1);
        tags = tags;
        active = null;

        updateActiveAfterBlur((active: number) => {
            if (active === index) {
                return null;
            } else if (active > index) {
                return active - 1;
            }

            return active;
        });
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

        const spliced = tags.splice(index - 1, 1)[0];
        tags[index - 1].name = spliced.name + tags[index - 1].name;
        tags = tags;
    }

    function joinWithNextTag(tag: TagType, index: number): void {
        if (isLast(index)) {
            return;
        }

        const spliced = tags.splice(index + 1, 1)[0];
        tags[index].name = tags[index].name + spliced.name;
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
            addEmptyTag();
            return;
        }
        console.log("movenext", index);

        active = null;
        activeAfterBlur = index + 1;

        /* await tick(); */
        /* input.setSelectionRange(0, 0); */
    }

    async function activate(index: number): Promise<void> {
        active = index;
    }

    async function checkForActivation(index: number): Promise<void> {
        const selection = window.getSelection()!;
        if (selection.isCollapsed) {
            await activate(index);
        }
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
                            on:tagdelete={() => deleteTag(tag, index)}
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
                            on:click={() => checkForActivation(index)}
                            on:tagdelete={() => deleteTag(tag, index)}
                        />
                    {/if}
                {/each}

                <div
                    class="tag-spacer flex-grow-1 align-self-stretch"
                    on:click={addEmptyTag}
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
