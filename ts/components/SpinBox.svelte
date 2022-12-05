<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { isDesktop } from "@tslib/platform";

    import IconConstrain from "./IconConstrain.svelte";
    import { chevronDown, chevronUp } from "./icons";

    export let value: number;
    export let step = 1;
    export let min = 1;
    export let max = 9999;

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
    {#if isDesktop()}
        <button
            class="decrement"
            disabled={value == min}
            tabindex="-1"
            on:click={() => {
                input.focus();
                if (value > min) {
                    change(-step);
                }
            }}
            on:mousedown={() =>
                longPress(() => {
                    if (value > min) {
                        change(-step);
                    }
                })}
            on:mouseup={() => {
                clearTimeout(pressTimer);
                pressed = false;
            }}
        >
            <IconConstrain>
                {@html chevronDown}
            </IconConstrain>
        </button>
        <button
            class="increment"
            disabled={value == max}
            tabindex="-1"
            on:click={() => {
                input.focus();
                if (value < max) {
                    change(step);
                }
            }}
            on:mousedown={() =>
                longPress(() => {
                    if (value < max) {
                        change(step);
                    }
                })}
            on:mouseup={() => {
                clearTimeout(pressTimer);
                pressed = false;
            }}
        >
            <IconConstrain>
                {@html chevronUp}
            </IconConstrain>
        </button>
    {/if}
</div>

<style lang="scss">
    @use "sass/button-mixins" as button;

    .spin-box {
        width: 100%;
        background: var(--canvas-inset);
        border: 1px solid var(--border);
        border-radius: var(--border-radius);
        overflow: hidden;
        position: relative;
        display: flex;
        justify-content: space-between;

        input {
            flex-grow: 1;
            border: none;
            outline: none;
            background: transparent;
            &::-webkit-inner-spin-button {
                display: none;
            }
            padding-left: 0.5em;
            padding-right: 0.5em;
        }

        &:hover {
            button {
                visibility: visible;
            }
        }
    }
    button {
        visibility: hidden;
        @include button.base($border: false);
        border-radius: 0;
        height: 100%;
    }
</style>
