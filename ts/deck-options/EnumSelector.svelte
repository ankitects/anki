<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { pageTheme } from "../sveltelib/theme";

    export let choices: string[];
    export let value: number = 0;
    export let disabled: number[] = [];
</script>

<select
    bind:value
    class:nightMode={$pageTheme.isDark}
    class:visible-down-arrow={$pageTheme.isDark}
    class="enum-selector form-select"
>
    {#each choices as choice, idx}
        <option value={idx} disabled={disabled.includes(idx)}>{choice}</option>
    {/each}
</select>

<style lang="scss">
    @use "sass/night-mode" as nightmode;
    @use "sass/button-mixins" as button;

    .nightMode {
        @include nightmode.input;
    }

    .enum-selector {
        /* overwrite Bootstrap */
        padding: 0.2rem 0.75rem;
    }

    .visible-down-arrow {
        /* override the default down arrow */
        background-image: button.down-arrow(white);
    }
</style>
