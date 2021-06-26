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
    import { attachId, getName } from "./tags";

    export let initialNames = ["en::foobar", "test", "def"];
    export let suggestions = ["en::idioms", "anki::functionality", "math"];

    export let size = isApplePlatform() ? 1.6 : 2.0;

    let active: number | null = null;
    let activeAfterBlur: number | null = null;

    let input: HTMLInputElement;
    let tags = initialNames.map(attachId);

    function isFirst(index: number): boolean {
        return index === 0;
    }

    function isLast(index: number): boolean {
        return index === tags.length - 1;
    }

    function decideNextActive() {
        if (typeof active === "number") {
            active = activeAfterBlur;
        }

        if (typeof activeAfterBlur === "number") {
            active = activeAfterBlur;
            activeAfterBlur = null;
        }
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
        activeAfterBlur = index + 1;
    }

    function appendEmptyTagAt(index: number): void {
        tags.splice(index + 1, 0, attachId(""));
        tags = tags;
        activeAfterBlur = index + 1;
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

        active = index - 1;
    }

    async function moveToNextTag(index: number): Promise<void> {
        if (isLast(index)) {
            addEmptyTag();
            return;
        }

        active = index + 1;

        await tick();
        input.setSelectionRange(0, 0);
    }

    function deactivate(index: number): void {
        active = null;
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
                            on:blur={decideNextActive}
                            on:tagupdate={() => addTagAt(index)}
                            on:tagadd={() => insertTagAt(index)}
                            on:tagdelete={() => deleteTagAt(index)}
                            on:tagjoinprevious={() => joinWithPreviousTag(index)}
                            on:tagjoinnext={() => joinWithNextTag(index)}
                            on:tagmoveprevious={() => moveToPreviousTag(index)}
                            on:tagmovenext={() => moveToNextTag(index)}
                        />
                    {:else}
                        <Tag
                            name={tag.name}
                            bind:blink={tag.blink}
                            on:click={() => checkForActivation(index)}
                            on:tagdelete={() => deleteTagAt(index)}
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
