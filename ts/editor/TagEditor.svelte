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
    let activeName = "";

    let autocompletionChoice: string | undefined;

    function setActiveAfterBlur(value: number): void {
        if (activeAfterBlur === null) {
            activeAfterBlur = value;
        }
    }

    function decideNextActive() {
        console.log("dna", active, activeAfterBlur);
        active = activeAfterBlur;
        activeAfterBlur = null;
    }

    function appendEmptyTag(): void {
        // used by tag badge and tag spacer
        const lastTag = tags[tags.length - 1];

        if (!lastTag || lastTag.name.length > 0) {
            tags.splice(tags.length, 0, attachId(""));
            tags = tags;
        }

        const tagsHadFocus = active === null;
        active = null;
        setActiveAfterBlur(tags.length - 1);

        if (tagsHadFocus) {
            decideNextActive();
        }
    }

    function appendTagAtAndFocus(index: number, name: string): void {
        tags.splice(index + 1, 0, attachId(name));
        tags = tags;
        active = null;
        setActiveAfterBlur(index + 1);
    }

    function checkIfUniqueName(newName: string): boolean {
        const names = tags.map(getName);
        const contained = names.indexOf(newName);
        if (contained >= 0) {
            tags[contained].blink();
            return false;
        }

        return true;
    }

    async function splitTag(index: number, start: number, end: number): Promise<void> {
        const current = activeName.slice(0, start);
        const splitOff = activeName.slice(end);

        activeName = current;
        appendTagAtAndFocus(index, splitOff);

        await tick();
        input.setSelectionRange(0, 0);
    }

    function insertTag(index: number): void {
        if (!checkIfUniqueName(activeName)) {
            tags.splice(index, 0, attachId(activeName));
            tags = tags;
        }
    }

    function deleteTag(index: number): TagType {
        const deleted = tags.splice(index, 1)[0];
        tags = tags;

        console.log("dt", activeAfterBlur, index);
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

    function deleteActiveTagIfNotUnique(tag: TagType, index: number): void {
        if (!tags.includes(tag)) {
            // already deleted
            return;
        }

        if (!checkIfUniqueName(index)) {
            deleteActiveTag(tag, index);
        }
    }

    function joinWithPreviousTag(index: number): void {
        if (isFirst(index)) {
            return;
        }

        const deleted = deleteTag(index - 1);
        activeName = deleted.name + activeName;
        tags = tags;
    }

    function joinWithNextTag(index: number): void {
        if (isLast(index)) {
            return;
        }

        const deleted = deleteTag(index + 1);
        activeName = activeName + deleted.name;
        tags = tags;
    }

    function moveToPreviousTag(index: number): void {
        if (isFirst(index)) {
            return;
        }
        console.log("moveprev", index);
        active = null;
        activeAfterBlur = index - 1;
    }

    async function moveToNextTag(index: number): Promise<void> {
        if (isLast(index)) {
            if (activeName.length !== 0) {
                appendTagAtAndFocus(index, "");
            }
            return;
        }

        active = null;
        activeAfterBlur = index + 1;

        await tick();
        input.setSelectionRange(0, 0);
    }
</script>

<StickyBottom>
    <div class="row-gap">
        <ButtonToolbar class="dropup d-flex flex-wrap align-items-center" {size}>
            <AddTagBadge on:click={appendEmptyTag} />

            <TagAutocomplete
                class="d-flex flex-column-reverse"
                {suggestions}
                search={tags[active ?? -1]?.name ?? ""}
                bind:choice={autocompletionChoice}
                let:updateAutocomplete
                let:destroyAutocomplete
            >
                {#each tags as tag, index (tag.id)}
                    {#if index === active}
                        <TagInput
                            id={tag.id}
                            bind:name={activeName}
                            bind:input
                            on:focus={() => (activeName = tag.name)}
                            on:keydown={updateAutocomplete}
                            on:tagsplit={({ detail }) =>
                                splitTag(index, detail.start, detail.end)}
                            on:tagadd={() => insertTag(index)}
                            on:tagdelete={() => deleteActiveTag(tag, index)}
                            on:tagjoinprevious={() => joinWithPreviousTag(index)}
                            on:tagjoinnext={() => joinWithNextTag(index)}
                            on:tagmoveprevious={() => moveToPreviousTag(index)}
                            on:tagmovenext={() => moveToNextTag(index)}
                            on:tagaccept={() => {
                                tag.name = activeName;
                                deleteActiveTagIfNotUnique(tag, index);
                                destroyAutocomplete();
                                decideNextActive();
                            }}
                        />
                    {:else}
                        <Tag
                            name={tag.name}
                            bind:blink={tag.blink}
                            on:click={() => (active = index)}
                            on:tagdelete={() => deleteTag(index)}
                        />
                    {/if}
                {/each}

                <div
                    class="tag-spacer flex-grow-1 align-self-stretch"
                    on:click={appendEmptyTag}
                />
            </TagAutocomplete>

            <div>
                a, aab, an: {active}
                {activeAfterBlur} "{activeName}";<br />{JSON.stringify(tags)}
            </div>
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
