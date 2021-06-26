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

    function checkIfContains(newName: string, names: string[]): boolean {
        const contained = names.indexOf(newName);
        if (contained >= 0) {
            tags[contained].blink = true;
            return true;
        }

        return false;
    }

    function checkIfContainsName(newName: string): boolean {
        const names = tags.map(getName);
        return checkIfContains(newName, names);
    }

    function checkIfContainsNameAt(index: number): boolean {
        const names = tags.map(getName);
        const newName = names.splice(index, 1, "")[0];
        return checkIfContains(newName, names);
    }

    function checkForDuplicateNameAt(index: number): void {
        if (checkIfContainsNameAt(index)) {
            deleteTagAt(index);
        }
    }

    function insertTagAt(index: number): boolean {
        const names = tags.map(getName);
        const newName = names.splice(index, 1)[0];
        let added = false;

        if (!checkIfContainsNameAt(index) && newName.length > 0) {
            tags.splice(index, 0, attachId(newName));
            added = true;
        }

        tags = tags;
        return added;
    }

    function deleteTagAt(index: number): void {
        console.log("eyo", index);
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

        tags[index - 1].active = true;
        tags[index].active = false;
        tags = tags;
    }

    async function moveToNextTag(index: number): Promise<void> {
        if (index === tags.length - 1 || tags.length === 1) {
            focusNewInput();
            return;
        }

        tags[index].active = false;
        tags[index + 1].active = true;
        tags = tags;

        await tick();

        (document.activeElement as HTMLInputElement).setSelectionRange(0, 0);
    }

    function appendTag(): boolean {
        let added = false;

        if (!checkIfContainsName(newName)) {
            tags.push(attachId(newName));
            added = true;
        }

        tags = tags;
        newName = "";

        return added;
    }

    function joinWithLastTag(): void {
        const popped = tags.pop();
        tags = tags;

        if (popped) {
            newName = popped.name + newName;
        }
    }

    async function moveToLastTag(): Promise<void> {
        const newTag = appendTag() ? tags[tags.length - 2] : tags[tags.length - 1];
        newTag.active = true;
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
                        bind:active={tag.active}
                        bind:blink={tag.blink}
                        on:keydown={() => {}}
                        on:blur={() => {}}
                        on:tagupdate={() => checkForDuplicateNameAt(index)}
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
                    on:tagmovenext={appendTag}
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
