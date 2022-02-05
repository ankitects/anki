<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import Dropdown from "bootstrap/js/dist/dropdown";
    import { onDestroy, setContext } from "svelte";

    import { dropdownKey } from "./context-keys";

    export let autoOpen = false;
    export let autoClose: boolean | "inside" | "outside" = true;

    export let toggleOpen = true;
    export let drop: "down" | "up" = "down";
    export let align: "start" | "end" | "auto" = "auto";

    let placement: string;

    $: {
        let blockPlacement: string;

        switch (drop) {
            case "down":
                blockPlacement = "bottom";
                break;
            case "up":
                blockPlacement = "top";
                break;
        }

        let inlinePlacement: string;

        switch (align) {
            case "start":
            case "end":
                inlinePlacement = `-${align}`;
                break;
            default:
                inlinePlacement = "";
                break;
        }

        placement = `${blockPlacement}${inlinePlacement}`;
    }

    $: dropClass = `drop${drop}`;

    export let skidding = 0;
    export let distance = 2;

    setContext(dropdownKey, {
        dropdown: true,
        "data-bs-toggle": "dropdown",
    });

    let dropdown: Dropdown;
    let api: Dropdown & { isVisible: () => boolean };

    function isVisible() {
        return (dropdown as any)._menu
            ? (dropdown as any)._menu.classList.contains("show")
            : false;
    }

    const noop = () => {};
    function createDropdown(toggle: HTMLElement): Dropdown {
        /* avoid focusing element toggle on menu activation */
        toggle.focus = noop;

        if (!toggleOpen) {
            /* do not open on clicking toggle */
            toggle.addEventListener = noop;
        }

        dropdown = new Dropdown(toggle, {
            autoClose,
            offset: [skidding, distance],
            popperConfig: { placement },
        } as any);

        if (autoOpen) {
            dropdown.show();
        }

        api = {
            show: dropdown.show.bind(dropdown),
            // TODO this is quite confusing, but having a noop function fixes Bootstrap
            // in the deck-options when not including Bootstrap via <script />
            toggle: () => {},
            /* toggle: dropdown.toggle.bind(dropdown), */
            hide: dropdown.hide.bind(dropdown),
            update: dropdown.update.bind(dropdown),
            dispose: dropdown.dispose.bind(dropdown),
            isVisible,
        } as any;

        return api;
    }

    onDestroy(() => dropdown?.dispose());
</script>

<div class="with-dropdown {dropClass}">
    <slot {createDropdown} dropdownObject={api} />
</div>

<style lang="scss">
    div {
        display: contents;
    }
</style>
