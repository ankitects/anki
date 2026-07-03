<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import Icon from "$lib/components/Icon.svelte";
    import IconButton from "$lib/components/IconButton.svelte";
    import { mdiCodeTags } from "$lib/components/icons";

    import { changePreviewHTMLView } from "./lib";

    export let iconSize;

    const moreTools = [
        {
            name: "code",
            title: "Code",
            icon: mdiCodeTags,
            action: changePreviewHTMLView,
        },
    ];
</script>

{#each moreTools as tool}
    <IconButton
        class="note-tool-icon-button right-border-radius"
        {iconSize}
        on:click={() => tool.action()}
        tooltip={tool.title}
    >
        <Icon icon={tool.icon} />
    </IconButton>
{/each}
