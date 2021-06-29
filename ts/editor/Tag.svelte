<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import { getContext, createEventDispatcher } from "svelte";
    import { nightModeKey } from "components/contextKeys";
    import Badge from "components/Badge.svelte";
    import { deleteIcon } from "./icons";

    export let name: string;

    const dispatch = createEventDispatcher();

    function deleteTag(): void {
        dispatch("tagdelete");
    }

    let flashing: boolean = false;

    export function flash() {
        flashing = true;
        setTimeout(() => (flashing = false), 300);
    }

    let showDelete = true;

    function hideDeleteOnCtrlShfit(event: MouseEvent) {
        showDelete = !event.ctrlKey && !event.shiftKey;
    }

    function showDeleteOnLeave() {
        showDelete = true;
    }

    function toggleDelete() {
        // TODO
    }

    const nightMode = getContext<boolean>(nightModeKey);
</script>

<button
    class="btn d-inline-flex align-items-center text-nowrap rounded ps-2 pe-1 me-1"
    class:flashing
    class:btn-day={!nightMode}
    class:btn-night={nightMode}
    tabindex="-1"
    on:mousemove={hideDeleteOnCtrlShfit}
    on:mouseleave={showDeleteOnLeave}
    on:keydown={toggleDelete}
    on:keyup={toggleDelete}
    on:click
>
    <span>{name}</span>
    <Badge
        class={`delete-icon rounded ms-1 mt-1${showDelete ? "" : " opacity-0"}`}
        on:click={deleteTag}>{@html deleteIcon}</Badge
    >
</button>

<style lang="scss">
    @use "ts/sass/button_mixins" as button;

    @keyframes flash {
        0% {
            filter: invert(0);
        }
        50% {
            filter: invert(0.4);
        }
        100% {
            filter: invert(0);
        }
    }

    button {
        padding: 0;

        &:focus,
        &:active {
            outline: none;
        }

        &.flashing {
            animation: flash 0.3s linear;
        }
    }

    @include button.btn-day($with-active: false);
    @include button.btn-night($with-active: false);

    $white-translucent: rgba(255 255 255 / 0.5);
    $dark-translucent: rgba(0 0 0 / 0.2);

    :global(.delete-icon > svg:hover) {
        .btn-day & {
            background-color: $dark-translucent;
        }

        .btn-night & {
            background-color: $white-translucent;
        }
    }
</style>
