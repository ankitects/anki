<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import IconConstrain from "./IconConstrain.svelte";
    import { chevronLeft, chevronRight } from "./icons";

    export let value = 1;
    export let step = 1;
    export let min = 1;
    export let max = 9999;

    const rtl: boolean = window.getComputedStyle(document.body).direction == "rtl";

    let input: HTMLInputElement;
    let focused = false;

    function decimalPlaces(value: number) {
        if (Math.floor(value) === value) return 0;
        return value.toString().split(".")[1].length || 0;
    }

    let stringValue: string;
    $: stringValue = value.toFixed(decimalPlaces(step));

    function update(this: HTMLInputElement): void {
        value = Math.min(max, Math.max(min, parseFloat(this.value)));
        if (value > max) {
            value = max;
        } else if (value < min) {
            value = min;
        }
    }

    function handleWheel(event: WheelEvent) {
        if (focused) {
            value += event.deltaY < 0 ? step : -step;
            event.preventDefault();
        }
    }
</script>

<div class="spin-box" on:wheel={handleWheel}>
    <button
        class="left"
        disabled={rtl ? value == max : value == min}
        on:click={() => {
            input.focus();
            if (rtl && value < max) {
                value += step;
            } else if (value > min) {
                value -= step;
            }
        }}
    >
        <IconConstrain>
            {@html chevronLeft}
        </IconConstrain>
    </button>
    <input
        type="number"
        pattern="[0-9]*"
        inputmode="numeric"
        {min}
        {max}
        {step}
        value={stringValue}
        bind:this={input}
        on:blur={update}
        on:focusin={() => (focused = true)}
        on:focusout={() => (focused = false)}
    />
    <button
        class="right"
        disabled={rtl ? value == min : value == max}
        on:click={() => {
            input.focus();
            if (rtl && value > min) {
                value -= step;
            } else if (value < max) {
                value += step;
            }
        }}
    >
        <IconConstrain>
            {@html chevronRight}
        </IconConstrain>
    </button>
</div>

<style lang="scss">
    @use "sass/button-mixins" as button;

    .spin-box {
        width: 100%;
        border: 1px solid var(--border);
        border-radius: var(--border-radius);
        overflow: hidden;
        position: relative;

        input {
            width: 100%;
            padding: 0.2rem 1.5rem 0.2rem 0.75rem;
            border: none;
            outline: none;
            text-align: center;
            &::-webkit-inner-spin-button {
                display: none;
            }
        }

        &:hover,
        &:focus-within {
            button {
                opacity: 1;
            }
        }
    }
    button {
        opacity: 0;
        position: absolute;
        @include button.base($border: false);

        &.left {
            inset: 0 auto 0 0;
            border-right: 1px solid var(--border);
        }
        &.right {
            position: absolute;
            inset: 0 0 0 auto;
            border-left: 1px solid var(--border);
        }
    }
</style>
