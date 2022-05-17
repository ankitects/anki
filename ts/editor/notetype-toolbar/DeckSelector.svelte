<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { writable } from "svelte/store";

    import TagEditMode from "../tag-editor/TagEditMode.svelte";
    import TagInput from "../tag-editor/TagInput.svelte";
    import Tag from "../tag-editor/TagInput.svelte";
    import WithAutocomplete from "../tag-editor/WithAutocomplete.svelte";

    const active = false;
    let notetypeName = "TheDeck";

    const show = writable(false);

    const noSuggestions = Promise.resolve(['a', 'b']);
    const suggestionsPromise: Promise<string[]> = noSuggestions;

    let activeInput: HTMLInputElement;
</script>

<div class="notetype-selector">
    <TagEditMode
        class="ms-0"
        name={notetypeName}
        tooltip="foo"
        {active}
        on:tagedit
        on:tagselect
        on:tagrange
        on:tagdelete
    />

    {#if active}
        <WithAutocomplete {suggestionsPromise} {show} on:update on:select on:choose>
            <TagInput
                class="position-absolute start-0 top-0 bottom-0 ps-2 py-0"
                disabled={false}
                bind:name={notetypeName}
                bind:input={activeInput}
                on:focus
                on:keydown
                on:keyup
                on:taginput
                on:tagsplit
                on:tagadd
                on:tagdelete
                on:tagselectall
                on:tagjoinprevious
                on:tagjoinnext
                on:tagmoveprevious
                on:tagmovenext
                on:tagaccept
            />
        </WithAutocomplete>
    {/if}
</div>
