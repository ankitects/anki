<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { writable } from "svelte/store";

    import Tag from "../tag-editor/Tag.svelte";
    import TagInput from "../tag-editor/TagInput.svelte";
    import WithAutocomplete from "../tag-editor/WithAutocomplete.svelte";
    import { notetypeIcon } from "./icons";

    export let name: string;

    const active = false;

    const suggestionsPromise = Promise.reject();
    const show = writable(false);

    let activeInput: HTMLInputElement;
</script>

<div class="notetype-selector">
    <Tag>{@html notetypeIcon} {name}</Tag>

    {#if active}
        <WithAutocomplete {suggestionsPromise} {show} on:update on:select on:choose>
            <TagInput
                class="position-absolute start-0 top-0 bottom-0 ps-2 py-0"
                disabled={false}
                bind:name
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
