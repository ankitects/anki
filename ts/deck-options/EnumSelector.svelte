<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { getContext } from "svelte";
    import { nightModeKey } from "components/context-keys";

    export let choices: string[];
    export let value: number = 0;

    const nightMode = getContext<boolean>(nightModeKey);
</script>

<select
    bind:value
    class:nightMode
    class:visible-down-arrow={nightMode}
    class="form-select"
>
    {#each choices as choice, idx}
        <option value={idx}>{choice}</option>
    {/each}
</select>

<style lang="scss">
    @use "ts/sass/night-mode" as nightmode;
    @use "ts/sass/button-mixins" as button;

    .nightMode {
        @include nightmode.input;
    }

    .visible-down-arrow {
        /* override the default down arrow */
        background-image: button.down-arrow(white);
    }
</style>
