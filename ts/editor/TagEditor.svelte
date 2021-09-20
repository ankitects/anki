<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import { createEventDispatcher, tick } from "svelte";
    import StickyFooter from "components/StickyFooter.svelte";
    import TagOptionsBadge from "./TagOptionsBadge.svelte";
    import TagEditMode from "./TagEditMode.svelte";
    import TagInput from "./TagInput.svelte";
    import Tag from "./Tag.svelte";
    import WithAutocomplete from "./WithAutocomplete.svelte";
    import ButtonToolbar from "components/ButtonToolbar.svelte";
    import type { Tag as TagType } from "./tags";
    import {
        attachId,
        getName,
        replaceWithUnicodeSeparator,
        replaceWithColons,
    } from "./tags";
    import { Tags } from "lib/proto";
    import { postRequest } from "lib/postrequest";

    export let size: number;
    export let wrap: boolean;

    /* TODO currently tags is only used for the initial setting */
    export let tags: string[] = [];
    let tagTypes: TagType[] = tags.map((tag) =>
        attachId(replaceWithUnicodeSeparator(tag))
    ) as TagType[];

    const dispatch = createEventDispatcher();
    const noSuggestions = Promise.resolve([]);
    let suggestionsPromise: Promise<string[]> = noSuggestions;

    function saveTags(): void {
        const tags = tagTypes.map((tag) => tag.name).map(replaceWithColons);
        dispatch("tagsupdate", { tags });

        suggestionsPromise = noSuggestions;
    }

    export function resetTags(tags: string[]): void {
        /* TODO I think once we move to Rust calls (web socket?) we might be able to refactor
        /* the process of setting tags on the TagEditor */
        tagTypes = tags.map((tag) => attachId(replaceWithUnicodeSeparator(tag)));
    }

    let active: number | null = null;
    let activeAfterBlur: number | null = null;
    let activeName = "";
    let activeInput: HTMLInputElement;

    let autocomplete: any;
    let autocompleteDisabled: boolean = false;

    async function fetchSuggestions(input: string): Promise<string[]> {
        const data = await postRequest(
            "/_anki/completeTag",
            Tags.CompleteTagRequest.encode(
                Tags.CompleteTagRequest.create({ input, matchLimit: 500 })
            ).finish()
        );
        const response = Tags.CompleteTagResponse.decode(data);
        return response.tags;
    }

    const withoutSingleColonAtStartOrEnd = /^:?([^:].*?[^:]):?$/;

    function updateSuggestions(): void {
        const activeTag = tagTypes[active!];
        const activeName = activeTag.name;

        autocompleteDisabled = activeName.length === 0;

        if (autocompleteDisabled) {
            suggestionsPromise = noSuggestions;
        } else {
            const withColons = replaceWithColons(activeName);
            const withoutSingleColons = withoutSingleColonAtStartOrEnd.test(withColons)
                ? withColons.replace(withoutSingleColonAtStartOrEnd, "$1")
                : withColons;

            suggestionsPromise = fetchSuggestions(withoutSingleColons).then(
                (names: string[]): string[] => {
                    autocompleteDisabled = names.length === 0;
                    return names.map(replaceWithUnicodeSeparator);
                }
            );
        }
    }

    function onAutocomplete(selected: string): void {
        const activeTag = tagTypes[active!];

        activeName = selected ?? activeTag.name;
        activeInput.setSelectionRange(Infinity, Infinity);
    }

    async function updateTagName(tag: TagType): Promise<void> {
        tag.name = activeName;
        tagTypes = tagTypes;

        await tick();
        if (activeInput) {
            autocomplete.update();
        }
    }

    function setActiveAfterBlur(value: number): void {
        if (activeAfterBlur === null) {
            activeAfterBlur = value;
        }
    }

    function appendEmptyTag(): void {
        // used by tag badge and tag spacer
        const lastTag = tagTypes[tagTypes.length - 1];

        if (!lastTag || lastTag.name.length > 0) {
            appendTagAndFocusAt(tagTypes.length - 1, "");
        }

        const tagsHadFocus = active === null;
        active = null;

        if (tagsHadFocus) {
            decideNextActive();
        }
    }

    function appendTagAndFocusAt(index: number, name: string): void {
        tagTypes.splice(index + 1, 0, attachId(name));
        tagTypes = tagTypes;
        setActiveAfterBlur(index + 1);
    }

    function isActiveNameUniqueAt(index: number): boolean {
        const names = tagTypes.map(getName);
        names.splice(index, 1);

        const contained = names.indexOf(activeName);
        if (contained >= 0) {
            tagTypes[contained >= index ? contained + 1 : contained].flash();
            return false;
        }

        return true;
    }

    async function enterBehavior(
        index: number,
        start: number,
        end: number
    ): Promise<void> {
        if (autocomplete.hasSelected()) {
            autocomplete.chooseSelected();
            await tick();
        }

        splitTag(index, start, end);
    }

    async function splitTag(index: number, start: number, end: number): Promise<void> {
        const current = activeName.slice(0, start);
        const splitOff = activeName.slice(end);

        activeName = current;
        // await tag to update its name, so it can normalize correctly
        await tick();

        appendTagAndFocusAt(index, splitOff);
        active = null;
        await tick();

        if (index === active) {
            // splitOff tag was rejected
            return;
        }
        activeInput.setSelectionRange(0, 0);
    }

    function insertTagKeepFocus(index: number): void {
        if (isActiveNameUniqueAt(index)) {
            tagTypes.splice(index, 0, attachId(activeName));
            active!++;
            tagTypes = tagTypes;
        }
    }

    function deleteTagAt(index: number): TagType {
        const deleted = tagTypes.splice(index, 1)[0];
        tagTypes = tagTypes;

        if (activeAfterBlur !== null && activeAfterBlur > index) {
            activeAfterBlur--;
        }

        return deleted;
    }

    function isFirst(index: number): boolean {
        return index === 0;
    }

    function isLast(index: number): boolean {
        return index === tagTypes.length - 1;
    }

    function joinWithPreviousTag(index: number): void {
        if (isFirst(index)) {
            return;
        }

        const deleted = deleteTagAt(index - 1);
        activeName = deleted.name + activeName;
        active!--;
        updateTagName(tagTypes[active!]);
    }

    function joinWithNextTag(index: number): void {
        if (isLast(index)) {
            return;
        }

        const deleted = deleteTagAt(index + 1);
        activeName = activeName + deleted.name;
        updateTagName(tagTypes[active!]);
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
        if (!tagTypes.includes(tag)) {
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

    function onKeydown(event: KeyboardEvent): void {
        switch (event.code) {
            case "ArrowUp":
                autocomplete.selectPrevious();
                event.preventDefault();
                break;

            case "ArrowDown":
                autocomplete.selectNext();
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
        }
    }

    function onKeyup(): void {
        if (activeName.length === 0) {
            autocomplete.hide();
        }
    }

    let selectionAnchor: number | null = null;
    let selectionFocus: number | null = null;

    function select(index: number) {
        tagTypes[index].selected = !tagTypes[index].selected;
        tagTypes = tagTypes;

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
            tagTypes[index].selected = true;
        }

        tagTypes = tagTypes;
    }

    function deselect() {
        tagTypes = tagTypes.map(
            (tag: TagType): TagType => ({ ...tag, selected: false })
        );
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

    /* TODO replace with navigator.clipboard once available */
    function copyToClipboard(content: string): void {
        const textarea = document.createElement("textarea");
        textarea.value = content;
        textarea.setAttribute("readonly", "");
        textarea.style.position = "absolute";
        textarea.style.left = "-9999px";
        document.body.appendChild(textarea);
        textarea.select();
        document.execCommand("copy");
        document.body.removeChild(textarea);
    }

    function selectAllTags() {
        tagTypes.forEach((tag) => (tag.selected = true));
        tagTypes = tagTypes;
    }

    function copySelectedTags() {
        const content = tagTypes
            .filter((tag) => tag.selected)
            .map((tag) => replaceWithColons(tag.name))
            .join("\n");
        copyToClipboard(content);
        deselect();
    }

    function deleteSelectedTags() {
        tagTypes
            .map((tag, index) => [tag.selected, index])
            .filter(([selected]) => selected)
            .reverse()
            .forEach(([, index]) => deleteTagAt(index as number));
        deselect();
        saveTags();
    }

    let height: number;
    let badgeHeight: number;

    // typically correct for rows < 7
    $: assumedRows = Math.floor(height / badgeHeight);
    $: shortenTags = shortenTags || assumedRows > 2;
</script>

<StickyFooter bind:height class="d-flex">
    {#if !wrap}
        <TagOptionsBadge
            --buttons-size="{size}rem"
            showSelectionsOptions={tagTypes.some((tag) => tag.selected)}
            bind:badgeHeight
            on:tagselectall={selectAllTags}
            on:tagcopy={copySelectedTags}
            on:tagdelete={deleteSelectedTags}
            on:click={appendEmptyTag}
        />
    {/if}

    <ButtonToolbar
        class="d-flex align-items-center w-100 px-1"
        {size}
        {wrap}
        on:focusout={deselectIfLeave}
    >
        {#if wrap}
            <TagOptionsBadge
                showSelectionsOptions={tagTypes.some((tag) => tag.selected)}
                bind:badgeHeight
                on:tagselectall={selectAllTags}
                on:tagcopy={copySelectedTags}
                on:tagdelete={deleteSelectedTags}
                on:click={appendEmptyTag}
            />
        {/if}

        {#each tagTypes as tag, index (tag.id)}
            <div
                class="position-relative tag-margins"
                class:hide-tag={index === active}
            >
                <TagEditMode
                    class="ms-0 tag-margins-inner"
                    name={index === active ? activeName : tag.name}
                    tooltip={tag.name}
                    active={index === active}
                    shorten={shortenTags}
                    bind:flash={tag.flash}
                    bind:selected={tag.selected}
                    on:tagedit={() => {
                        active = index;
                        deselect();
                    }}
                    on:tagselect={() => select(index)}
                    on:tagrange={() => selectRange(index)}
                    on:tagdelete={() => {
                        deselect();
                        deleteTagAt(index);
                        saveTags();
                    }}
                />

                {#if index === active}
                    <div class="adjust-position">
                        <WithAutocomplete
                            drop="up"
                            class="d-flex flex-column cap-items"
                            {suggestionsPromise}
                            on:update={updateSuggestions}
                            on:select={({ detail }) => onAutocomplete(detail.selected)}
                            on:choose={({ detail }) => onAutocomplete(detail.chosen)}
                            let:createAutocomplete
                        >
                            <TagInput
                                id={tag.id}
                                class="tag-input position-absolute start-0 top-0 ps-2 py-0"
                                disabled={autocompleteDisabled}
                                bind:name={activeName}
                                bind:input={activeInput}
                                on:focus={() => {
                                    activeName = tag.name;
                                    autocomplete = createAutocomplete(activeInput);
                                }}
                                on:keydown={onKeydown}
                                on:keyup={onKeyup}
                                on:taginput={() => updateTagName(tag)}
                                on:tagsplit={({ detail }) =>
                                    enterBehavior(index, detail.start, detail.end)}
                                on:tagadd={() => insertTagKeepFocus(index)}
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
                    </div>
                {/if}
            </div>
        {/each}

        <div
            class="tag-spacer flex-grow-1 align-self-stretch"
            on:click={appendEmptyTag}
        />

        <div class="position-relative tag-margins hide-tag zero-width-tag">
            <!-- makes sure footer does not resize when adding first tag -->
            <Tag>SPACER</Tag>
        </div>
    </ButtonToolbar>
</StickyFooter>

<style lang="scss">
    .tag-spacer {
        cursor: text;
    }

    .hide-tag :global(.tag) {
        opacity: 0;
    }

    .zero-width-tag :global(.tag) {
        width: 0;
        pointer-events: none;
        padding-left: 0 !important;
        padding-right: 0 !important;
    }

    .tag-margins {
        margin-bottom: 0.15rem;

        :global(.tag-margins-inner) {
            margin-right: 2px;
        }
    }

    .adjust-position {
        :global(.tag-input) {
            /* recreates positioning of Tag component */
            border-left: 1px solid transparent;
        }

        :global(.cap-items) {
            max-height: 7rem;
            overflow-y: scroll;
        }
    }
</style>
