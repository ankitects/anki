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

    const noSuggestions = Promise.resolve(['a', 'b']);
    const suggestionsPromise: Promise<string[]> = noSuggestions;

    const show = writable(false);

    let activeInput: HTMLInputElement;

    function updateSuggestions() {
    }
</script>

<div class="notetype-selector" on:click={() => (active = !active)} on:mousedown|preventDefault>
    <GhostButton>
        <svelte:fragment slot="icon">{@html notetypeIcon}</svelte:fragment>
        <svelte:fragment slot="label">
            <span class="notetype-selector-relative" class:hide-label={active}>
                <span class="notetype-selector-label">{name}</span>

                {#if active}
                    <WithAutocomplete
                        {suggestionsPromise}
                        {show}
                        on:update={console.log}
                        on:select
                        on:choose
                    >
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
            </span>
        </svelte:fragment>
    </GhostButton>
</div>

<style lang="scss">
    .notetype-selector-relative {
        position: relative;
    }

    .hide-label .notetype-selector-label {
        opacity: 0;
    }
</style>
