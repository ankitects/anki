<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import { tick } from "svelte";
    import { isApplePlatform } from "lib/platform";
    import { bridgeCommand } from "lib/bridgecommand";
    import StickyBottom from "components/StickyBottom.svelte";
    import AddTagBadge from "./AddTagBadge.svelte";
    import Tag from "./Tag.svelte";
    import TagInput from "./TagInput.svelte";
    import WithAutocomplete from "./WithAutocomplete.svelte";
    import ButtonToolbar from "components/ButtonToolbar.svelte";
    import type { Tag as TagType } from "./tags";
    import { attachId, getName } from "./tags";

    export let size = isApplePlatform() ? 1.6 : 2.0;
    export let tags: TagType[] = [];

    export let suggestions = ["en::idioms", "anki::functionality", "math"];

    export function resetTags(names: string[]): void {
        tags = names.map(attachId);
    }

    function saveTags(): void {
        bridgeCommand(`saveTags:${JSON.stringify(tags.map((tag) => tag.name))}`);
    }

    let active: number | null = null;
    let activeAfterBlur: number | null = null;
    let activeName = "";
    let activeInput: HTMLInputElement;

    function onAutocomplete({ detail }) {
        const activeTag = tags[active!];
        const autocompletionChoice = detail.choice;

        console.log(autocompletionChoice, activeTag);
        if (autocompletionChoice) {
            activeName = autocompletionChoice;
        } else {
            activeName = activeTag.name;
        }
    }

    let addOrPop = true;
    function updateSuggestions(): void {
        if (suggestions.length === 0) {
            addOrPop = false;
        } else if (suggestions.length > 3) {
            addOrPop = true;
        }

        if (addOrPop) {
            suggestions.pop();
            suggestions = suggestions;
        } else {
            suggestions = suggestions.concat(["another"]);
        }
    }

    function updateWithTagName(tag: TagType): void {
        tag.name = activeName;
        tags = tags;
    }

    function setActiveAfterBlur(value: number): void {
        if (activeAfterBlur === null) {
            activeAfterBlur = value;
        }
    }

    function appendEmptyTag(): void {
        // used by tag badge and tag spacer
        const lastTag = tags[tags.length - 1];

        if (!lastTag || lastTag.name.length > 0) {
            appendTagAndFocusAt(tags.length - 1, "");
        }

        const tagsHadFocus = active === null;
        active = null;

        if (tagsHadFocus) {
            decideNextActive();
        }
    }

    function appendTagAndFocusAt(index: number, name: string): void {
        tags.splice(index + 1, 0, attachId(name));
        tags = tags;
        setActiveAfterBlur(index + 1);
    }

    function isActiveNameUniqueAt(index: number): boolean {
        const names = tags.map(getName);
        names.splice(index, 1);

        const contained = names.indexOf(activeName);
        console.log(
            "isActiveUnique",
            active,
            index,
            activeName,
            JSON.stringify(names),
            contained
        );
        if (contained >= 0) {
            tags[contained].flash();
            return false;
        }

        return true;
    }

    async function splitTag(index: number, start: number, end: number): Promise<void> {
        const current = activeName.slice(0, start);
        const splitOff = activeName.slice(end);

        activeName = current;
        appendTagAndFocusAt(index, splitOff);
        active = null;

        await tick();

        if (index === active) {
            // splitOff tag was rejected
            return;
        }
        activeInput.setSelectionRange(0, 0);
    }

    function insertTag(index: number): void {
        if (!isActiveNameUniqueAt(index)) {
            tags.splice(index, 0, attachId(activeName));
            tags = tags;
        }
    }

    function deleteTagAt(index: number): TagType {
        const deleted = tags.splice(index, 1)[0];
        tags = tags;

        console.log("dt", activeAfterBlur, index);
        if (activeAfterBlur !== null && activeAfterBlur > index) {
            activeAfterBlur--;
        }

        return deleted;
    }

    function isFirst(index: number): boolean {
        return index === 0;
    }

    function isLast(index: number): boolean {
        return index === tags.length - 1;
    }

    function joinWithPreviousTag(index: number): void {
        if (isFirst(index)) {
            return;
        }

        const deleted = deleteTagAt(index - 1);
        activeName = deleted.name + activeName;
        active = active! - 1;
        console.log("joinprevious", activeName, active);
        tags = tags;
    }

    function joinWithNextTag(index: number): void {
        if (isLast(index)) {
            return;
        }

        const deleted = deleteTagAt(index + 1);
        activeName = activeName + deleted.name;
        tags = tags;
    }

    function moveToPreviousTag(index: number): void {
        console.log("moveprevious", active, index);

        if (isFirst(index)) {
            return;
        }

        activeAfterBlur = index - 1;
        active = null;
    }

    async function moveToNextTag(index: number): Promise<void> {
        if (isLast(index)) {
            if (activeName.length !== 0) {
                appendTagAndFocusAt(index, "");
                active = null;
            }
            return;
        }

        activeAfterBlur = index + 1;
        active = null;

        await tick();
        activeInput.setSelectionRange(0, 0);
    }

    function deleteTagIfNotUnique(tag: TagType, index: number): void {
        if (!tags.includes(tag)) {
            // already deleted
            return;
        }

        if (!isActiveNameUniqueAt(index)) {
            deleteTagAt(index);
        }
    }

    function decideNextActive() {
        console.log("dna", active, activeAfterBlur);
        active = activeAfterBlur;
        activeAfterBlur = null;
    }

    function update(event: KeyboardEvent, autocomplete): void {
        switch (event.code) {
            case "ArrowUp":
                autocomplete.selectNext();
                event.preventDefault();
                break;

            case "ArrowDown":
                autocomplete.selectPrevious();
                event.preventDefault();
                break;

            case "Tab":
                if (event.shiftKey) {
                    autocomplete.selectNext();
                } else {
                    autocomplete.selectPrevious();
                }
                event.preventDefault();
                break;

            case "Enter":
                autocomplete.chooseSelected();
                event.preventDefault();
                break;

            default:
                autocomplete.update();
                break;
        }
    }
</script>

<StickyBottom>
    <div class="row-gap">
        <ButtonToolbar class="dropup d-flex flex-wrap align-items-center" {size}>
            <AddTagBadge on:click={appendEmptyTag} />

            {#each tags as tag, index (tag.id)}
                {#if index === active}
                    <WithAutocomplete
                        class="d-flex flex-column-reverse"
                        {suggestions}
                        on:autocomplete={onAutocomplete}
                        on:update={updateSuggestions}
                        let:createAutocomplete
                        let:autocomplete
                    >
                        <TagInput
                            id={tag.id}
                            bind:name={activeName}
                            bind:input={activeInput}
                            on:focus={() => {
                                activeName = tag.name;
                                createAutocomplete(activeInput);
                            }}
                            on:keydown={(event) => update(event, autocomplete)}
                            on:input={() => updateWithTagName(tag)}
                            on:tagsplit={({ detail }) =>
                                splitTag(index, detail.start, detail.end)}
                            on:tagadd={() => insertTag(index)}
                            on:tagdelete={() => deleteTagAt(index)}
                            on:tagjoinprevious={() => joinWithPreviousTag(index)}
                            on:tagjoinnext={() => joinWithNextTag(index)}
                            on:tagmoveprevious={() => moveToPreviousTag(index)}
                            on:tagmovenext={() => moveToNextTag(index)}
                            on:tagaccept={() => {
                                deleteTagIfNotUnique(tag, index);
                                if (tag) {
                                    updateWithTagName(tag);
                                }
                                saveTags();
                                decideNextActive();
                            }}
                        />
                    </WithAutocomplete>
                {:else}
                    <Tag
                        name={tag.name}
                        bind:flash={tag.flash}
                        on:click={() => (active = index)}
                        on:tagdelete={() => {
                            deleteTagAt(index);
                            saveTags();
                        }}
                    />
                {/if}
            {/each}

            <div
                class="tag-spacer flex-grow-1 align-self-stretch"
                on:click={appendEmptyTag}
            />
            {active}
            {activeAfterBlur}
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
