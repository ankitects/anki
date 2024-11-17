<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { isApplePlatform } from "@tslib/platform";
    import { createEventDispatcher, tick } from "svelte";
    import type { Writable } from "svelte/store";

    import Popover from "$lib/components/Popover.svelte";
    import WithFloating from "$lib/components/WithFloating.svelte";

    import AutocompleteItem from "./AutocompleteItem.svelte";

    export let suggestionsPromise: Promise<string[]>;
    export let show: Writable<boolean>;

    let suggestionsItems: string[] = [];
    $: suggestionsPromise.then((items) => {
        show.set(items.length > 0);
        if (isApplePlatform() && navigator.userAgent.match(/Chrome\/77/)) {
            items = items.slice(0, 10);
        }
        suggestionsItems = items;
    });

    let selected: number | null = null;
    let active: boolean = false;

    const dispatch = createEventDispatcher<{
        update: void;
        /* Selected should be displayed to the user, but it is not accepted */
        select: { selected: string };
        /* Autocompletion action should finish with "chosen" */
        choose: { chosen: string };
    }>();

    /**
     * Select as currently highlighted item
     */
    function incrementSelected(): void {
        if (selected === null) {
            selected = 0;
        } else if (selected >= suggestionsItems.length - 1) {
            selected = null;
        } else {
            selected++;
        }
    }

    function decrementSelected(): void {
        if (selected === null) {
            selected = suggestionsItems.length - 1;
        } else if (selected === 0) {
            selected = null;
        } else {
            selected--;
        }
    }

    async function updateSelected(): Promise<void> {
        dispatch("select", { selected: suggestionsItems[selected ?? -1] });
    }

    async function selectNext(): Promise<void> {
        incrementSelected();
        await updateSelected();
    }

    async function selectPrevious(): Promise<void> {
        decrementSelected();
        await updateSelected();
    }

    /**
     * Choose as accepted suggestion
     */
    async function chooseSelected() {
        if (!suggestionsItems.length) {
            return;
        }

        active = true;
        dispatch("choose", { chosen: suggestionsItems[selected ?? -1] });

        await tick();
        show.set(false);
    }

    async function update() {
        await tick();
        dispatch("update");
    }

    function hasSelected(): boolean {
        return selected !== null;
    }

    function createAutocomplete() {
        const api = {
            selectPrevious,
            selectNext,
            chooseSelected,
            update,
            hasSelected,
        };

        return api;
    }

    function setSelected(index: number): void {
        selected = index;
        active = true;
    }

    function setSelectedAndActive(index: number): void {
        setSelected(index);
    }

    async function selectIndex(index: number): Promise<void> {
        active = false;
        dispatch("select", { selected: suggestionsItems[index] });
    }

    function selectIfMousedown(event: MouseEvent, index: number): void {
        if (event.buttons === 1) {
            setSelected(index);
        }
    }
</script>

<WithFloating
    keepOnKeyup
    show={$show}
    preferredPlacement="top"
    portalTarget={document.body}
    let:asReference
    on:close={() => show.set(false)}
>
    <span class="autocomplete-reference" use:asReference>
        <slot {createAutocomplete} />
    </span>

    <Popover slot="floating" --popover-padding-inline="0">
        <div class="autocomplete-menu">
            {#each suggestionsItems as suggestion, index}
                {#if index === selected}
                    <AutocompleteItem
                        selected
                        {active}
                        {suggestion}
                        on:mousedown={() => setSelectedAndActive(index)}
                        on:mouseup={() => {
                            selectIndex(index);
                            chooseSelected();
                        }}
                        on:mouseenter={(event) => selectIfMousedown(event, index)}
                        on:mouseleave={() => (active = false)}
                    >
                        {suggestion}
                    </AutocompleteItem>
                {:else}
                    <AutocompleteItem
                        {suggestion}
                        on:mousedown={() => setSelectedAndActive(index)}
                        on:mouseup={() => {
                            selectIndex(index);
                            chooseSelected();
                        }}
                        on:mouseenter={(event) => selectIfMousedown(event, index)}
                    >
                        {suggestion}
                    </AutocompleteItem>
                {/if}
            {/each}
        </div>
    </Popover>
</WithFloating>

<style lang="scss">
    .autocomplete-reference {
        position: absolute;
        top: 0;
        left: 0;
        right: 0;
        bottom: 0;

        /* Make sure that text in TagInput perfectly overlaps with Tag */
        border-left: 1px solid transparent;
        border-bottom: 1px solid transparent;
    }

    .autocomplete-menu {
        display: flex;
        flex-flow: column nowrap;

        width: 80vw;
        max-height: 30vh;

        font-size: 13px;
        overflow-x: hidden;
        text-overflow: ellipsis;
        overflow-y: auto;
    }
</style>
