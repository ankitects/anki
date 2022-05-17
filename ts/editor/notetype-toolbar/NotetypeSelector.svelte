<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { tick } from "svelte";
    import { writable } from "svelte/store";

    import { Generic, notetypes } from "../../lib/proto";
    import TagInput from "../tag-editor/TagInput.svelte";
    import WithAutocomplete from "../tag-editor/WithAutocomplete.svelte";
    import GhostButton from "./GhostButton.svelte";
    import { notetypeIcon } from "./icons";

    export let name: string;

    let active = false;

    const noSuggestions = Promise.resolve([]);
    let suggestionsPromise: Promise<string[]> = noSuggestions;

    $: suggestionsPromise.then(console.log);

    const show = writable(false);

    let activeInput: HTMLInputElement;

    function updateSuggestions(): void {
        const suggestions = notetypes.getNotetypeNames(Generic.Empty.create());
        suggestionsPromise = suggestions.then(({ entries }) =>
            entries
                .map(({ name: suggestion }) => suggestion)
                .filter((suggestion) => suggestion.includes(name)),
        );
    }

    function onKeydown(event: KeyboardEvent): void {
        switch (event.code) {
            case "ArrowUp":
                autocomplete.selectPrevious();
                event.preventDefault();
                break;

            case "ArrowDown":
                autocomplete.selectNext();
                event.preventDefault();
                break;

            case "Tab":
                if (!$show) {
                    break;
                } else if (event.shiftKey) {
                    autocomplete.selectPrevious();
                } else {
                    autocomplete.selectNext();
                }
                event.preventDefault();
                break;

            case "Enter":
                autocomplete.chooseSelected();
                event.preventDefault();
                break;
        }
    }

    function onAutocomplete(selected: string): void {
        name = selected;

        const inputEnd = activeInput.value.length;
        activeInput.setSelectionRange(inputEnd, inputEnd);
    }

    async function updateNotetypeName(): Promise<void> {
        await tick();
        autocomplete.update();
    }

    let autocomplete: any;
</script>

<div
    class="notetype-selector"
    on:click={() => (active = !active)}
    on:mousedown|preventDefault
>
    <GhostButton>
        <svelte:fragment slot="icon">{@html notetypeIcon}</svelte:fragment>
        <svelte:fragment slot="label">
            <span class="notetype-selector-relative" class:hide-label={active}>
                <span class="notetype-selector-label">{name}</span>

                {#if active}
                    <WithAutocomplete
                        {suggestionsPromise}
                        {show}
                        placement="bottom-start"
                        on:update={updateSuggestions}
                        on:select={({ detail }) => onAutocomplete(detail.selected)}
                        on:choose={({ detail }) => {
                            onAutocomplete(detail.chosen);
                            activeInput.blur();
                        }}
                        let:createAutocomplete
                    >
                        <TagInput
                            disabled={false}
                            bind:name
                            bind:input={activeInput}
                            --base-font-size="14px"
                            on:focus={() => (autocomplete = createAutocomplete())}
                            on:blur={() => (active = false)}
                            on:keydown={onKeydown}
                            on:keyup
                            on:taginput={updateNotetypeName}
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
