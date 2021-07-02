<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import Dropdown from "bootstrap/js/dist/dropdown";

    import { setContext, onDestroy } from "svelte";
    import { dropdownKey } from "./context-keys";

    export let autoOpen = false;
    export let autoClose: boolean | "inside" | "outside" = true;

    export let placement = "bottom-start";
    export let toggleOpen = true;
    export let drop: "down" | "up" | "left" | "right" = "down";

    $: dropClass = `drop${drop}`;

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
            popperConfig: (defaultConfig: Record<string, any>) => ({
                ...defaultConfig,
                placement,
            }),
        } as any);

        if (autoOpen) {
            dropdown.show();
        }

        let api = {
            show: dropdown.show.bind(dropdown),
            toggle: dropdown.toggle.bind(dropdown),
            hide: dropdown.hide.bind(dropdown),
            update: dropdown.update.bind(dropdown),
            dispose: dropdown.dispose.bind(dropdown),
            isVisible,
        };

        return api;
    }

    onDestroy(() => dropdown?.dispose());
</script>

<div class={dropClass}>
    <slot {createDropdown} dropdownObject={api} />
</div>

<style lang="scss">
    div {
        display: contents;
    }
</style>
