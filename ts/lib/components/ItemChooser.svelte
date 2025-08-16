<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts" generics="Item extends { id: bigint, name: string }">
    import { magnifyIcon, mdiClose } from "./icons";
    import Icon from "./Icon.svelte";
    import IconConstrain from "./IconConstrain.svelte";
    import LabelButton from "./LabelButton.svelte";
    import Modal from "./Modal.svelte";
    import type { IconData } from "./types";
    import * as tr from "@generated/ftl";
    import Shortcut from "./Shortcut.svelte";

    interface Props {
        title: string;
        selectedItem?: Item | null;
        items: Item[];
        icon: IconData;
        keyCombination: string;
        tooltip: string;
        onChange?: (item: Item) => void;
    }

    let {
        title,
        onChange,
        icon,
        items,
        selectedItem = $bindable(null),
        keyCombination,
        tooltip,
    }: Props = $props();
    let modal: Modal | null = $state(null);
    let searchQuery = $state("");

    const filteredItems = $derived(
        searchQuery.trim() === ""
            ? items
            : items.filter((item) =>
                  item.name.toLowerCase().includes(searchQuery.toLowerCase()),
              ),
    );

    function onSelect(item: Item) {
        selectedItem = item;
        onChange?.(item);
        modal?.hide();
    }

    function openModal() {
        searchQuery = "";
        modal?.show();
    }

    export function select(itemId: bigint) {
        const item = items.find((item) => item.id === itemId);
        selectedItem = item ? item : null;
    }

    $effect(() => {
        if (!selectedItem && items.length > 0) {
            selectedItem = items[0];
            onChange?.(selectedItem);
        }
    });
</script>

<LabelButton {tooltip} on:click={openModal} class="chooser-button">
    {selectedItem?.name ?? "…"}
</LabelButton>

<Shortcut {keyCombination} on:action={() => modal?.toggle()} />
<Modal bind:this={modal} dialogClass="modal-lg">
    <div slot="header" class="modal-header">
        <IconConstrain iconSize={90}>
            <Icon {icon} />
        </IconConstrain>
        <h5 class="modal-title">{title}</h5>
        <button
            type="button"
            class="btn-close"
            data-bs-dismiss="modal"
            aria-label="Close"
        ></button>
    </div>

    <div slot="body" class="modal-body">
        <div class="search-container">
            <div class="search-input-wrapper">
                <div class="search-icon">
                    <IconConstrain iconSize={70}>
                        <Icon icon={magnifyIcon} />
                    </IconConstrain>
                </div>
                <input
                    type="text"
                    class="search-input"
                    placeholder={tr.actionsSearch()}
                    bind:value={searchQuery}
                />
                {#if searchQuery}
                    <button
                        type="button"
                        class="clear-search"
                        onclick={() => (searchQuery = "")}
                        aria-label="Clear search"
                    >
                        <IconConstrain iconSize={60}>
                            <Icon icon={mdiClose} />
                        </IconConstrain>
                    </button>
                {/if}
            </div>
        </div>
        <div class="item-grid">
            {#each filteredItems as item (item.id)}
                <button
                    class="item-card"
                    class:selected={selectedItem?.id === item.id}
                    onclick={() => onSelect(item)}
                    aria-label="Select {item.name}"
                >
                    <h6 class="item-title">{item.name}</h6>
                </button>
            {/each}
        </div>
    </div>
</Modal>

<style lang="scss">
    @use "../sass/button-mixins" as button;

    :global(.label-button.chooser-button) {
        width: 100%;
    }

    .modal-header {
        display: flex;
        align-items: center;
        gap: 0.75rem;

        .modal-title {
            margin: 0;
            font-weight: 600;
            color: var(--fg);
        }
    }

    .search-input-wrapper {
        position: relative;
        display: flex;
        align-items: center;
        background: var(--canvas);
        border: 1px solid var(--border-subtle);
        border-radius: 0.375rem;
        transition: border-color 0.2s ease;

        &:focus-within {
            border-color: var(--border-strong);
        }
    }

    .search-icon {
        padding: 0.5rem 0.75rem;
        color: var(--fg-subtle);
        pointer-events: none;
    }

    .search-input {
        flex: 1;
        padding: 0.5rem 0.75rem 0.5rem 0;
        border: none;
        background: transparent;
        color: var(--fg);
        font-size: 0.9rem;
        outline: none;

        &::placeholder {
            color: var(--fg-subtle);
        }
    }

    .clear-search {
        padding: 0.25rem;
        margin-right: 0.5rem;
        border: none;
        background: transparent;
        color: var(--fg-subtle);
        border-radius: 0.25rem;

        &:hover {
            background: var(--canvas-inset);
            color: var(--fg);
        }
    }

    .item-grid {
        display: grid;
        grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
        gap: 1rem;
        padding: 0.5rem 0;
    }

    :global(.item-card) {
        @include button.base(
            $border: true,
            $with-hover: true,
            $with-active: true,
            $with-disabled: false
        );
        @include button.border-radius;

        padding: 1rem;
        text-align: start;
        background: var(--canvas-elevated);
        border: 1px solid var(--border-subtle);

        &:hover {
            background: var(--canvas-inset);
            border-color: var(--border);
            transform: translateY(-1px);
            box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
        }

        &.selected {
            border-color: var(--border-focus);
        }
    }

    .item-title {
        margin: 0 0 0.25rem 0;
        font-size: 1rem;
        font-weight: 600;
        color: inherit;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
    }

    .modal-body {
        padding: 1.5rem;
        max-height: 80vh;
        overflow-y: auto;
    }

    .modal-header {
        padding: 1.5rem;
        border-bottom: 1px solid var(--border-subtle);
        background: var(--canvas-elevated);
    }
</style>
