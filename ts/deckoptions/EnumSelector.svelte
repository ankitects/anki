<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { getContext } from "svelte";
    import { nightModeKey } from "components/contextKeys";

    export let choices: string[];
    export let value: number = 0;

    const nightMode = getContext<boolean>(nightModeKey);
</script>

<select bind:value class:visible-down-arrow={nightMode} class="form-select">
    {#each choices as choice, idx}
        <option value={idx}>{choice}</option>
    {/each}
</select>

<style lang="scss">
    @use "ts/sass/button_mixins" as button;

    .visible-down-arrow {
        /* override the default down arrow */
        @include button.select-night-mode;
    }
</style>
