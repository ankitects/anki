<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { writable } from "svelte/store";

    import TagInput from "../tag-editor/TagInput.svelte";
    import WithAutocomplete from "../tag-editor/WithAutocomplete.svelte";
    import GhostButton from "./GhostButton.svelte";
    import { notetypeIcon } from "./icons";

    export let name: string;

    let active = false;

    const suggestionsPromise = Promise.reject();
    const show = writable(false);

    let activeInput: HTMLInputElement;
</script>

<div class="notetype-selector" on:click={() => (active = !active)} on:mousedown|preventDefault>
    <GhostButton>
        <svelte:fragment slot="icon">{@html notetypeIcon}</svelte:fragment>
        <svelte:fragment slot="label">
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
            {:else}
                <span>{name}</span>
            {/if}
        </svelte:fragment>
    </GhostButton>
</div>
