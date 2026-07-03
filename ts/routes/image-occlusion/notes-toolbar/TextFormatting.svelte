<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "@generated/ftl";

    import Icon from "$lib/components/Icon.svelte";
    import IconButton from "$lib/components/IconButton.svelte";
    import {
        mdiFormatBold,
        mdiFormatItalic,
        mdiFormatUnderline,
    } from "$lib/components/icons";
    import { execCommand } from "$lib/domlib";

    export let iconSize;

    const textFormatting = [
        {
            name: "b",
            title: tr.editingBoldText(),
            icon: mdiFormatBold,
            action: "bold",
        },
        {
            name: "i",
            title: tr.editingItalicText(),
            icon: mdiFormatItalic,
            action: "italic",
        },
        {
            name: "u",
            title: tr.editingUnderlineText(),
            icon: mdiFormatUnderline,
            action: "underline",
        },
    ];

    const textFormat = (tool: { name; title; icon; action }) => {
        execCommand(tool.action, false, tool.name);
    };
</script>

{#each textFormatting as tool}
    <IconButton
        id={"note-tool-" + tool.name}
        class="note-tool-icon-button {tool.name === 'b' ? 'left-border-radius' : ''}"
        {iconSize}
        tooltip={tool.title}
        on:click={() => {
            // setActiveTool(tool);
            textFormat(tool);
        }}
    >
        <Icon icon={tool.icon} />
    </IconButton>
{/each}

<style lang="scss">
    :global(.note-tool-icon-button) {
        padding: 4px !important;
        border-radius: 2px !important;
        padding: 0px 6px 0px 6px !important;
    }

    :global(.left-border-radius) {
        border-radius: 5px 0 0 5px !important;
    }

    :global(.right-border-radius) {
        border-radius: 0 5px 5px 0 !important;
    }
</style>
