<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "@tslib/ftl";
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
        <div
            class="spinner decrement"
            class:active={value > min}
            tabindex="-1"
            title={tr.actionsDecrementValue()}
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
        </div>
        <div
            class="spinner increment"
            class:active={value < max}
            tabindex="-1"
            title={tr.actionsIncrementValue()}
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
        </div>
    {/if}
</div>

<style lang="scss">
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
            .spinner {
                opacity: 0.1;
                &.active {
                    opacity: 0.4;
                    cursor: pointer;
                    &:hover {
                        opacity: 1;
                    }
                }
            }
        }
    }
    .spinner {
        opacity: 0;
        height: 100%;
    }
</style>
