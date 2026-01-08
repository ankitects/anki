<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { completeTag } from "@generated/backend";
    import { tagActionsShortcutsKey } from "@tslib/context-keys";
    import { isArrowDown, isArrowUp } from "@tslib/keys";
    import { createEventDispatcher, onDestroy, setContext, tick } from "svelte";
    import type { Writable } from "svelte/store";
    import { writable } from "svelte/store";

    import Shortcut from "$lib/components/Shortcut.svelte";
    import { execCommand } from "$lib/domlib";

    import TagDisplayModeButton from "./TagDisplayModeButton.svelte";
    import { TagOptionsButton } from "./tag-options-button";
    import TagEditMode from "./TagEditMode.svelte";
    import TagInput from "./TagInput.svelte";
    import type { Tag as TagType } from "./tags";
    import {
        attachId,
        getName,
        replaceWithColons,
        replaceWithUnicodeSeparator,
    } from "./tags";
    import TagSpacer from "./TagSpacer.svelte";
    import WithAutocomplete from "./WithAutocomplete.svelte";

    export let tags: Writable<string[]>;
    export let keyCombination: string = "Control+Shift+T";
    export let displayFull: boolean = false;

    const selectAllShortcut = "Control+A";
    const copyShortcut = "Control+C";
    const removeShortcut = "Backspace";
    setContext(tagActionsShortcutsKey, {
        selectAllShortcut,
        copyShortcut,
        removeShortcut,
    });

    let tagTypes: TagType[];
    function tagsToTagTypes(tags: string[]): void {
        tagTypes = tags.map(
            (tag: string): TagType => attachId(replaceWithUnicodeSeparator(tag)),
        );
    }

    $: tagsToTagTypes($tags);

    const show = writable(false);
    const dispatch = createEventDispatcher();
    const noSuggestions = Promise.resolve([]);
    let suggestionsPromise: Promise<string[]> = noSuggestions;

    function saveTags(): void {
        const tags = tagTypes.map((tag: TagType) => tag.name).map(replaceWithColons);
        dispatch("tagsupdate", { tags });

        suggestionsPromise = noSuggestions;
    }

    let active: number | null = null;
    let activeAfterBlur: number | null = null;
    let activeName = "";
    let activeInput: HTMLInputElement;

    let autocomplete: any;
    let autocompleteDisabled: boolean = false;

    async function fetchSuggestions(input: string): Promise<string[]> {
        const { tags } = await completeTag({ input, matchLimit: 500 });
        return tags;
    }

    const withoutSingleColonAtStartOrEnd = /^:?([^:].*?[^:]):?$/;

    function updateSuggestions(): void {
        const activeTag = tagTypes[active!];
        const activeName = activeTag!.name;

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
                },
            );
        }
    }

    function onAutocomplete(selected: string): void {
        const activeTag = tagTypes[active!];

        activeName = selected ?? activeTag.name;
        const inputEnd = activeInput.value.length;
        activeInput.setSelectionRange(inputEnd, inputEnd);
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

    export function appendEmptyTag(): void {
        // used by tag badge and tag spacer
        deselect();
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
        activeInput.blur();
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
        activeInput.blur();

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
        if (isArrowUp(event)) {
            autocomplete.selectPrevious();
            event.preventDefault();
            return;
        } else if (isArrowDown(event)) {
            autocomplete.selectNext();
            event.preventDefault();
            return;
        }

        switch (event.code) {
            case "Tab":
                if (!$show) {
                    break;
                } else if (event.shiftKey) {
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
            (tag: TagType): TagType => ({ ...tag, selected: false }),
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
        execCommand("copy");
        document.body.removeChild(textarea);
    }

    function selectAllTags() {
        for (const tag of tagTypes) {
            tag.selected = true;
        }

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
            .map((tag, index): [boolean, number] => [tag.selected, index])
            .filter(([selected]) => selected)
            .reverse()
            .forEach(([, index]) => deleteTagAt(index));
        deselect();
        saveTags();
    }

    let height: number;
    let badgeHeight: number;

    // typically correct for rows < 7
    $: assumedRows = Math.floor(height / badgeHeight);
    $: shortenTags = displayFull ? false : shortenTags || assumedRows > 2;
    $: anyTagsSelected = tagTypes.some((tag) => tag.selected);

    // Track editor width for tag truncation
    let editorElement: HTMLDivElement;
    let editorWidth: number = 0;
    let resizeObserver: ResizeObserver | null = null;
    let resizeTimeout: ReturnType<typeof setTimeout> | null = null;

    function updateEditorWidth(): void {
        if (editorElement) {
            editorWidth = editorElement.clientWidth;
        }
    }

    function debouncedUpdateWidth(): void {
        if (resizeTimeout) {
            clearTimeout(resizeTimeout);
        }
        resizeTimeout = setTimeout(updateEditorWidth, 32);
    }

    $: if (editorElement && !resizeObserver) {
        resizeObserver = new ResizeObserver(debouncedUpdateWidth);
        resizeObserver.observe(editorElement);
        updateEditorWidth();
    }

    onDestroy(() => {
        resizeObserver?.disconnect();
        if (resizeTimeout) {
            clearTimeout(resizeTimeout);
        }
    });
</script>

{#if anyTagsSelected}
    <Shortcut keyCombination={selectAllShortcut} on:action={selectAllTags} />
    <Shortcut keyCombination={copyShortcut} on:action={copySelectedTags} />
    <Shortcut keyCombination={removeShortcut} on:action={deleteSelectedTags} />
{/if}

<div
    class="tag-editor"
    class:display-full={displayFull}
    on:focusout={deselectIfLeave}
    bind:offsetHeight={height}
    bind:this={editorElement}
>
    <div class="tag-header">
        <TagOptionsButton
            bind:badgeHeight
            tagsSelected={anyTagsSelected}
            on:tagselectall={selectAllTags}
            on:tagcopy={copySelectedTags}
            on:tagdelete={deleteSelectedTags}
            on:tagappend={appendEmptyTag}
            {keyCombination}
            --icon-align="baseline"
        />

        <TagDisplayModeButton full={displayFull} on:displaymodechange --icon-align="baseline" />
    </div>

    {#each tagTypes as tag, index (tag.id)}
        <div class="tag-relative" class:hide-tag={index === active}>
            <TagEditMode
                class="ms-0"
                name={index === active ? activeName : tag.name}
                tooltip={tag.name}
                active={index === active}
                shorten={shortenTags}
                truncateMiddle={displayFull}
                {editorWidth}
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
                <WithAutocomplete
                    {suggestionsPromise}
                    {show}
                    on:update={updateSuggestions}
                    on:select={({ detail }) => onAutocomplete(detail.selected)}
                    on:choose={({ detail }) => {
                        onAutocomplete(detail.chosen);
                        splitTag(index, detail.chosen.length, detail.chosen.length);
                    }}
                    let:createAutocomplete
                >
                    <TagInput
                        id={tag.id}
                        class="position-absolute start-0 end-0 top-0 bottom-0 ps-2 py-0"
                        disabled={autocompleteDisabled}
                        bind:name={activeName}
                        bind:input={activeInput}
                        on:focus={() => {
                            dispatch("tagsFocused");
                            activeName = tag.name;
                            autocomplete = createAutocomplete();
                        }}
                        on:keydown={onKeydown}
                        on:keyup={() => {
                            if (activeName.length === 0) {
                                show?.set(false);
                            }
                        }}
                        on:taginput={() => updateTagName(tag)}
                        on:tagsplit={({ detail }) =>
                            splitTag(index, detail.start, detail.end)}
                        on:tagadd={() => insertTagKeepFocus(index)}
                        on:tagdelete={() => deleteTagAt(index)}
                        on:tagselectall={async () => {
                            if (tagTypes.length <= 1) {
                                // Noop if no other tags exist
                                return;
                            }

                            activeInput.blur();
                            // Ensure blur events are processed first
                            await tick();

                            selectAllTags();
                        }}
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
            {/if}
        </div>
    {/each}

    <TagSpacer on:click={appendEmptyTag} />
</div>

<style lang="scss">
    .tag-editor {
        display: flex;
        flex-grow: 1;
        flex-flow: row wrap;
        align-items: flex-end;
        background: var(--canvas-elevated);
        border: 1px solid var(--border);
        border-radius: var(--border-radius);
        padding: 6px;
        margin: 1px 3px 3px 1px;

        &:focus-within {
            outline-offset: -1px;
            outline: 2px solid var(--border-focus);
        }

        &.display-full {
            max-height: 50vh;
            overflow-y: auto;

            .tag-header {
                flex: 0 0 auto;
            }

            .tag-relative {
                flex: 1 1 100%;
                min-height: calc(var(--font-size, 15px) * 1.5 + 2px);
            }

            :global(.tag-spacer) {
                flex: 1 1 100%;
                min-height: calc(var(--font-size, 15px) * 1.5 + 2px);
                margin-top: 0;
            }

            .hide-tag ~ :global(.tag-spacer) {
                min-height: 0;
                height: 0;
                overflow: hidden;
            }
        }
    }

    .tag-header {
        display: flex;
        align-items: center;
        margin-right: 0.75rem;
    }

    .tag-relative {
        position: relative;
        padding: 0 1px;
    }

    .hide-tag :global(.tag) {
        visibility: hidden;
    }
</style>
