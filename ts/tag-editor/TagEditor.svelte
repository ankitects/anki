<script lang="typescript">
    import type { I18n } from "anki/i18n";

    import Tag from "./Tag.svelte";
    import TagInput from "./TagInput.svelte";
    import DeleteIcon from "./DeleteIcon.svelte";

    export let i18n: I18n;
    export let nightMode: boolean;

    export let tags: string[];

    let activeTag: number?;

    function tagActivated(index: number): void {
        activeTag = index;
    }

    function tagDeactivated(): void {
        activeTag = null;
    }

    function tagDeleted(index: number): void {
        tags = [...tags.slice(0, index), ...tags.slice(index + 1)];
    }

    $: {
        i18n;
        nightMode;
    }
</script>

<span>Tags</span>

<span>
    {#each tags as tagname, index}
        {#if index === activeTag}
            <TagInput bind:name={tagname} on:blur={tagDeactivated} autofocus={true} />
        {:else}
            <Tag name={tagname}  on:click={() => tagActivated(index)}>
                <DeleteIcon slot="after"  on:click={() => tagDeleted(index)}></DeleteIcon>
            </Tag>
        {/if}
    {/each}
</span>

<TagInput />
