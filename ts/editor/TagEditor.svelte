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

    let suggestionsPromise: Promise<string[]> = Promise.resolve([]);

    function updateSuggestions(): void {
        suggestionsPromise = Promise.resolve([
            "en::vocabulary",
            "en::idioms",
            Math.random().toString(36).substring(2),
        ]);
    }

    function onAutocomplete(selected: string): void {
        const activeTag = tags[active!];

        activeName = selected ?? activeTag.name;
        activeInput.setSelectionRange(Infinity, Infinity);
    }

    function onChosen(chosen: string) {
        onAutocomplete(chosen);
        splitTag(active!, Infinity, Infinity);
    }

    function updateTagName(tag: TagType): void {
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
        if (contained >= 0) {
            tags[contained >= index ? contained + 1 : contained].flash();
            return false;
        }

        return true;
    }

    async function enterBehavior(
        index: number,
        start: number,
        end: number,
        autocomplete: any
    ): Promise<void> {
        if (autocomplete.isVisible()) {
            autocomplete.chooseSelected();
        } else {
            splitTag(index, start, end);
        }
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
        active!--;
        updateTagName(tags[active!]);
    }

    function joinWithNextTag(index: number): void {
        if (isLast(index)) {
            return;
        }

        const deleted = deleteTagAt(index + 1);
        activeName = activeName + deleted.name;
        updateTagName(tags[active!]);
    }

    function moveToPreviousTag(index: number): void {
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
        active = activeAfterBlur;
        activeAfterBlur = null;
    }

    function isPrintableKey(event: KeyboardEvent): boolean {
        return event.key.length === 1;
    }

    function isDeletionKey(event: KeyboardEvent): boolean {
        return event.code === "Backspace" || event.code === "Delete";
    }

    function onKeydown(event: KeyboardEvent, autocomplete: any): void {
        const visible = autocomplete.isVisible();
        const printable = isPrintableKey(event);
        const deletion = isDeletionKey(event);

        if (!visible) {
            if (printable || deletion) {
                autocomplete.show();
            } else {
                return;
            }
        }

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
                    autocomplete.selectPrevious();
                } else {
                    autocomplete.selectNext();
                }
                event.preventDefault();
                break;

            case "Enter":
                autocomplete.chooseSelected();
                event.preventDefault();
                break;

            default:
                if (printable || deletion) {
                    autocomplete.update();
                }

                break;
        }
    }

    function onKeyup(_event: KeyboardEvent, autocomplete: any): void {
        if (activeName.length === 0) {
            autocomplete.hide();
        }
    }

    let selectionAnchor: number | null = null;
    let selectionFocus: number | null = null;

    function select(index: number) {
        tags[index].selected = !tags[index].selected;
        tags = tags;

        selectionAnchor = index;
    }

    function selectRange(index: number) {
        if (selectionAnchor === null) {
            select(index);
            return;
        }

        selectionFocus = index;

        const from = Math.min(selectionAnchor, selectionFocus);
        const to = Math.max(selectionAnchor, selectionFocus);

        for (let index = from; index <= to; index++) {
            tags[index].selected = true;
        }

        tags = tags;
    }

    function deselect() {
        tags = tags.map((tag: TagType): TagType => ({ ...tag, selected: false }));
        selectionAnchor = null;
        selectionFocus = null;
    }

    function deselectIfLeave(event: FocusEvent) {
        const toolbar = event.currentTarget as HTMLDivElement;
        if (
            event.relatedTarget === null ||
            !toolbar.contains(event.relatedTarget as Node)
        ) {
            deselect();
        }
    }
</script>

<StickyBottom>
    <ButtonToolbar
        class="dropup d-flex flex-wrap align-items-center"
        {size}
        on:focusout={deselectIfLeave}
    >
        <div class="pb-1">
            <AddTagBadge on:click={appendEmptyTag} />
        </div>

        {#each tags as tag, index (tag.id)}
            <div class="pb-1">
                {#if index === active}
                    <WithAutocomplete
                        class="d-flex flex-column-reverse dropup"
                        {suggestionsPromise}
                        on:update={updateSuggestions}
                        on:autocomplete={({ detail }) =>
                            onAutocomplete(detail.selected)}
                        on:choose={({ detail }) => onChosen(detail.chosen)}
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
                            on:keydown={(event) => onKeydown(event, autocomplete)}
                            on:keyup={(event) => onKeyup(event, autocomplete)}
                            on:input={() => updateTagName(tag)}
                            on:tagsplit={({ detail }) =>
                                enterBehavior(
                                    index,
                                    detail.start,
                                    detail.end,
                                    autocomplete
                                )}
                            on:tagadd={() => insertTag(index)}
                            on:tagdelete={() => deleteTagAt(index)}
                            on:tagjoinprevious={() => joinWithPreviousTag(index)}
                            on:tagjoinnext={() => joinWithNextTag(index)}
                            on:tagmoveprevious={() => moveToPreviousTag(index)}
                            on:tagmovenext={() => moveToNextTag(index)}
                            on:tagaccept={() => {
                                deleteTagIfNotUnique(tag, index);
                                if (tag) {
                                    updateTagName(tag);
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
                        bind:selected={tag.selected}
                        on:tagedit={() => {
                            active = index;
                            deselect();
                        }}
                        on:tagselect={() => select(index)}
                        on:tagrange={() => selectRange(index)}
                        on:tagdelete={() => {
                            deleteTagAt(index);
                            saveTags();
                        }}
                    />
                {/if}
            </div>
        {/each}

        <div
            class="tag-spacer flex-grow-1 align-self-stretch"
            on:click={appendEmptyTag}
        />
    </ButtonToolbar>
</StickyBottom>

<style lang="scss">
    .tag-spacer {
        cursor: text;
    }
</style>
