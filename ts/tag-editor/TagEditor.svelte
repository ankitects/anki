<script lang="typescript">
    import type { I18n } from "anki/i18n";

    import Tag from "./Tag.svelte";
    import TagInput from "./TagInput.svelte";
    import DeleteIcon from "./DeleteIcon.svelte";

    export let i18n: I18n;
    export let nightMode: boolean;

    export let tags: string[];

    let tagElements: Element[] = [];
    let tagDeletes: Element[] = [];

    function tagActivated(event: MouseEvent): void {
        console.log('activated', event)
    }

    function tagDeleted(event: MouseEvent): void {
        console.log('deleted', event)
    }

    $: {
        tagElements;
        i18n;
        nightMode;
        console.log(tagElements, tags);
    }
</script>

<span>Tags</span>

<span>
    {#each tags as tagname, index}
        <Tag name={tagname} bind:this={tagElements[index]} on:click={tagActivated}>
            <DeleteIcon slot="after" bind:this={tagDeletes[index]} on:click={tagDeleted}></DeleteIcon>
        </Tag>
    {/each}
</span>

<TagInput />
