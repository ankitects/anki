<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "@generated/ftl";

    import Col from "$lib/components/Col.svelte";
    import Row from "$lib/components/Row.svelte";

    import NotesToolbar from "./notes-toolbar/NotesToolbar.svelte";
    import { notesDataStore, tagsWritable } from "./store";
    import Tags from "./Tags.svelte";

    const notesFields = [
        {
            id: "header",
            title: tr.notetypesHeader(),
            divValue: "",
            textareaValue: "",
        },
        {
            id: "back-extra",
            title: tr.notetypesBackExtraField(),
            divValue: "",
            textareaValue: "",
        },
    ];
    $: notesDataStore.set(notesFields);
</script>

<div class="note-toolbar">
    <NotesToolbar />
</div>

{#each notesFields as field}
    <Row --cols={1}>
        <Col --col-size={1}>
            {field.title}
        </Col>
    </Row>
    <Row --cols={1}>
        <div class="note-container">
            <div
                id="{field.id}--div"
                bind:innerHTML={field.divValue}
                class="text-editor"
                on:input={() => {
                    field.textareaValue = field.divValue;
                }}
                contenteditable
            ></div>
            <textarea
                id="{field.id}--textarea"
                class="text-area"
                bind:value={field.textareaValue}
            ></textarea>
        </div>
    </Row>
{/each}
<div style="margin-top: 10px;">
    <Tags {tagsWritable} />
</div>

<style lang="scss">
    .text-area {
        height: 120px;
        width: 100%;
        display: none;
        background: var(--canvas-elevated);
        border: 2px solid var(--border);
        border-radius: var(--border-radius);
        outline: none;
        resize: none;
        overflow: auto;
    }

    .text-editor {
        height: 80px;
        border: 1px solid var(--border);
        border-radius: var(--border-radius);
        padding: 5px;
        overflow: auto;
        outline: none;
        background: var(--canvas-elevated);
    }

    .note-toolbar {
        margin-left: 106px;
        margin-top: 2px;
        display: flex;
        overflow-x: auto;
        height: 38px;
    }

    ::-webkit-scrollbar {
        width: 0.1em !important;
        height: 0.1em !important;
    }

    .note-container {
        width: 100%;
    }
</style>
