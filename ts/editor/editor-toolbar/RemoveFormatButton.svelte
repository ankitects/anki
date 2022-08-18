<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { onMount } from "svelte";

    import CheckBox from "../../components/CheckBox.svelte";
    import DropdownItem from "../../components/DropdownItem.svelte";
    import DropdownMenu from "../../components/DropdownMenu.svelte";
    import { withButton } from "../../components/helpers";
    import IconButton from "../../components/IconButton.svelte";
    import Shortcut from "../../components/Shortcut.svelte";
    import WithDropdown from "../../components/WithDropdown.svelte";
    import type { MatchType } from "../../domlib/surround";
    import * as tr from "../../lib/ftl";
    import { altPressed, shiftPressed } from "../../lib/keys";
    import { getPlatformString } from "../../lib/shortcuts";
    import { singleCallback } from "../../lib/typing";
    import { surrounder } from "../rich-text-input";
    import type { RemoveFormat } from "./EditorToolbar.svelte";
    import { context as editorToolbarContext } from "./EditorToolbar.svelte";
    import { eraserIcon } from "./icons";
    import { arrowIcon } from "./icons";

    const { removeFormats } = editorToolbarContext.get();

    function filterForKeys(formats: RemoveFormat[], value: boolean): string[] {
        return formats
            .filter((format) => format.active === value)
            .map((format) => format.key);
    }

    let activeKeys: string[];
    $: activeKeys = filterForKeys($removeFormats, true);

    let inactiveKeys: string[];
    $: inactiveKeys = filterForKeys($removeFormats, false);

    let showFormats: RemoveFormat[];
    $: showFormats = $removeFormats.filter(
        (format: RemoveFormat): boolean => format.show,
    );

    function remove(): void {
        surrounder.remove(activeKeys, inactiveKeys);
    }

    function onItemClick(event: MouseEvent, format: RemoveFormat): void {
        if (altPressed(event)) {
            const value = shiftPressed(event);

            for (const format of showFormats) {
                format.active = value;
            }
        }

        format.active = !format.active;
        $removeFormats = $removeFormats;
    }

    const keyCombination = "Control+R";

    let disabled: boolean;

    onMount(() => {
        const surroundElement = document.createElement("span");

        function matcher(
            element: HTMLElement | SVGElement,
            match: MatchType<never>,
        ): void {
            if (
                element.tagName === "SPAN" &&
                element.className.length === 0 &&
                element.style.cssText.length === 0
            ) {
                match.remove();
            }
        }

        const simpleSpans = {
            matcher,
            surroundElement,
        };

        const key = "simple spans";

        removeFormats.update((formats: RemoveFormat[]): RemoveFormat[] => [
            ...formats,
            {
                key,
                name: key,
                show: false,
                active: true,
            },
        ]);

        return singleCallback(
            surrounder.active.subscribe((value) => (disabled = !value)),
            surrounder.registerFormat(key, simpleSpans),
        );
    });
</script>

<IconButton
    tooltip="{tr.editingRemoveFormatting()} ({getPlatformString(keyCombination)})"
    {disabled}
    on:click={remove}
    --border-left-radius="5px"
>
    {@html eraserIcon}
</IconButton>

<Shortcut {keyCombination} on:action={remove} />

<div class="hide-after">
    <WithDropdown autoClose="outside" let:createDropdown --border-right-radius="5px">
        <IconButton
            tooltip={tr.editingSelectRemoveFormatting()}
            {disabled}
            widthMultiplier={0.5}
            on:mount={withButton(createDropdown)}
        >
            {@html arrowIcon}
        </IconButton>

        <DropdownMenu on:mousedown={(event) => event.preventDefault()}>
            {#each showFormats as format (format.name)}
                <DropdownItem on:click={(event) => onItemClick(event, format)}>
                    <CheckBox bind:value={format.active} />
                    <span class="d-flex-inline ps-3">{format.name}</span>
                </DropdownItem>
            {/each}
        </DropdownMenu>
    </WithDropdown>
</div>

<style lang="scss">
    .hide-after {
        display: contents;

        :global(.dropdown-toggle::after) {
            display: none;
        }
    }
</style>
