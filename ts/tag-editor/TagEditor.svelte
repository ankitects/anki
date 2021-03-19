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

    let activeTag: ?number;
    let newTagname = "";

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
        const normalized = normalizeTagname(newTagname);

        if (normalized) {
            tags = [normalized, ...tags].sort();
        }

        newTagname = "";
    }

    tags.sort();

    $: {
        i18n;
        nightMode;
    }
</script>

<span>Tags</span>

<span class="tags d-inline-flex flex-wrap justify-content-between">
    {#each tags as tagname, index}
        {#if index === activeTag}
            <TagInputEdit bind:name={tagname} on:blur={tagsUpdated} />
        {:else}
            <Tag name={tagname} on:click={() => tagActivated(index)}>
                <DeleteIcon slot="after" on:click={() => tagDeleted(index)} />
            </Tag>
        {/if}
    {/each}

    <TagInputNew bind:name={newTagname} on:blur={tagAdded} />
</span>
