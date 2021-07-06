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
    import SelectedTagBadge from "./SelectedTagBadge.svelte";
    import Tag from "./Tag.svelte";
    import TagInput from "./TagInput.svelte";
    import WithAutocomplete from "./WithAutocomplete.svelte";
    import ButtonToolbar from "components/ButtonToolbar.svelte";
    import { controlPressed } from "lib/keys";
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

    let autocomplete: any;
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
        end: number
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

    function insertTagKeepFocus(index: number): void {
        if (isActiveNameUniqueAt(index)) {
            tags.splice(index, 0, attachId(activeName));
            active!++;
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
        return event.key.length === 1 && !controlPressed(event);
    }

    function isDeletionKey(event: KeyboardEvent): boolean {
        return event.code === "Backspace" || event.code === "Delete";
    }

    function onKeydown(event: KeyboardEvent): void {
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

    function onKeyup(_event: KeyboardEvent): void {
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
        tags.forEach((tag) => (tag.selected = true));
        tags = tags;
    }

    function copySelectedTags() {
        const content = tags
            .filter((tag) => tag.selected)
            .map((tag) => tag.name)
            .join("\n");
        copyToClipboard(content);
        deselect();
    }

    function deleteSelectedTags() {
        tags.map((tag, index) => [tag.selected, index])
            .filter(([selected]) => selected)
            .reverse()
            .forEach(([, index]) => deleteTagAt(index as number));
        deselect();
        saveTags();
    }
</script>

<StickyBottom>
    <ButtonToolbar
        class="d-flex flex-wrap align-items-center mx-1"
        {size}
        on:focusout={deselectIfLeave}
    >
        <div class="gap" on:mousedown|preventDefault>
            {#if tags.some((tag) => tag.selected)}
                <SelectedTagBadge
                    --badge-align="-webkit-baseline-middle"
                    on:tagselectall={selectAllTags}
                    on:tagcopy={copySelectedTags}
                    on:tagdelete={deleteSelectedTags}
                />
            {:else}
                <AddTagBadge
                    --badge-align="-webkit-baseline-middle"
                    on:click={appendEmptyTag}
                />
            {/if}
        </div>

        {#each tags as tag, index (tag.id)}
            <div class="position-relative gap" class:hide-tag={index === active}>
                <Tag
                    class="me-1"
                    name={index === active ? activeName : tag.name}
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
                            class="d-flex flex-column-reverse"
                            {suggestionsPromise}
                            on:update={updateSuggestions}
                            on:select={({ detail }) => onAutocomplete(detail.selected)}
                            on:choose={({ detail }) => onChosen(detail.chosen)}
                            let:createAutocomplete
                        >
                            <TagInput
                                id={tag.id}
                                class="tag-input position-absolute top-0 start-0 ps-2 py-0"
                                bind:name={activeName}
                                bind:input={activeInput}
                                on:focus={() => {
                                    activeName = tag.name;
                                    autocomplete = createAutocomplete(activeInput);
                                }}
                                on:keydown={onKeydown}
                                on:keyup={onKeyup}
                                on:input={() => updateTagName(tag)}
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
    </ButtonToolbar>
</StickyBottom>

<style lang="scss">
    .tag-spacer {
        cursor: text;
    }

    .hide-tag :global(.tag) {
        opacity: 0;
    }

    .gap {
        margin-bottom: 0.15rem;
    }

    .adjust-position :global(.tag-input) {
        /* recreates positioning of Tag component */
        border-left: 1px solid transparent;
    }
</style>
