<script lang="typescript">
    import type { I18n } from "anki/i18n";

    import Tag from "./Tag.svelte";
    import DeleteIcon from "./DeleteIcon.svelte";

    export let i18n: I18n;
    export let nightMode: boolean;

    export let tags: string[];

    $: {
        i18n;
        nightMode;
        console.log(tags);
    }
</script>

<span>Tags</span>

{#each tags as tagname}
    <Tag name={tagname}>
        <DeleteIcon slot="after"></DeleteIcon>
    </Tag>
{/each}
