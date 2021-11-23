<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    let input: HTMLInputElement;

    export function focus(): void {
        input.focus();
    }

    export function blur(): void {
        input.blur();
    }

    export function isFocusTrap(element: Element): boolean {
        return element === input;
    }
</script>

<input bind:this={input} class="focus-trap" readonly tabindex="-1" on:focus />

<style lang="scss">
    .focus-trap {
        display: block;
        width: 0px;
        height: 0;
        padding: 0;
        margin: 0;
        border: none;
        outline: none;
        -webkit-appearance: none;
        background: none;
        resize: none;
        appearance: none;
    }
</style>
