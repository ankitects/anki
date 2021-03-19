<script lang="typescript">
    import type { I18n } from "anki/i18n";

    import Tag from "./Tag.svelte";
    import TagInputEdit from "./TagInputEdit.svelte";
    import TagInputNew from "./TagInputNew.svelte";
    import DeleteIcon from "./DeleteIcon.svelte";
    import { normalizeTagname } from "./helpers";

    export let i18n: I18n;
    export let nightMode: boolean;

    export let tags: string[];

    let activeTag: number;
    let tagnameNew: string = "";
    let tagInputNew: HTMLInputElement;

    function tagsUpdated(): void {
        activeTag = null;
        tags = tags.sort();
    }

    function tagActivated(index: number): void {
        activeTag = index;
    }

    function tagDeleted(index: number): void {
        tags = [...tags.slice(0, index), ...tags.slice(index + 1)];
    }

    function tagAdded(event: FocusEvent): void {
        const normalized = normalizeTagname(tagnameNew);

        if (normalized) {
            tags = [normalized, ...tags].sort();
        }

        tagnameNew = "";
    }

    tags.sort();

    $: {
        i18n;
        nightMode;
    }
</script>

<style lang="scss">
    ol {
        padding-left: 0.5rem;
        margin-top: 0.5rem;
        margin-bottom: 0.5rem;
    }

    li {
        position: relative;
        display: inline;
        margin-bottom: 0.5rem;
    }
</style>

<span>Tags</span>

<ol class="d-flex flex-wrap" on:click={() => tagInputNew.focus()}>
    {#each tags as tagname, index}
        <li>
            {#if index === activeTag}
                <TagInputEdit bind:name={tagname} on:blur={tagsUpdated} />
            {:else}
                <Tag name={tagname} on:click={() => tagActivated(index)}>
                    <DeleteIcon slot="after" on:click={() => tagDeleted(index)} />
                </Tag>
            {/if}
        </li>
    {/each}

    <li>
        <TagInputNew bind:name={tagnameNew} bind:input={tagInputNew} on:blur={tagAdded} />
    </li>
</ol>
