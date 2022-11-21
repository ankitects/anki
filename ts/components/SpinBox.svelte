<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import IconConstrain from "./IconConstrain.svelte";
    import { chevronLeft, chevronRight } from "./icons";

    export let value: number;
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
    $: if (value) stringValue = value.toFixed(decimalPlaces(step));

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

    function change(step: number): void {
        value += step;
        if (pressed) {
            setTimeout(() => change(step), timeout);
        }
    }

    const progression = [1500, 1250, 1000, 750, 500, 250];

    async function longPress(func: Function): Promise<void> {
        pressed = true;
        timeout = 128;
        pressTimer = setTimeout(func, 250);

        for (const delay of progression) {
            timeout = await new Promise((resolve) =>
                setTimeout(() => resolve(pressed ? timeout / 2 : 128), delay),
            );
        }
    }

    let pressed = false;
    let timeout: number;
    let pressTimer: any;
</script>

<div class="spin-box" on:wheel={handleWheel}>
    <button
        class="left"
        disabled={rtl ? value == max : value == min}
        on:click={() => {
            input.focus();
            if (rtl && value < max) {
                change(step);
            } else if (value > min) {
                change(-step);
            }
        }}
        on:mousedown={() =>
            longPress(() => {
                if (rtl && value < max) {
                    change(step);
                } else if (value > min) {
                    change(-step);
                }
            })}
        on:mouseup={() => {
            clearTimeout(pressTimer);
            pressed = false;
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
                change(-step);
            } else if (value < max) {
                change(step);
            }
        }}
        on:mousedown={() =>
            longPress(() => {
                if (rtl && value > min) {
                    change(-step);
                } else if (value < max) {
                    change(step);
                }
            })}
        on:mouseup={() => {
            clearTimeout(pressTimer);
            pressed = false;
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
            background: var(--canvas-elevated);
            color: var(--fg);
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
            top: 0;
            right: auto;
            bottom: 0;
            left: 0;
            border-right: 1px solid var(--border);
        }
        &.right {
            position: absolute;
            right: 0;
            left: auto;
            border-left: 1px solid var(--border);
        }
    }
</style>
