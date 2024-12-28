<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "@generated/ftl";
    import { isDesktop } from "@tslib/platform";
    import { tick } from "svelte";

    import { chevronDown, chevronUp } from "$lib/components/icons";

    import Icon from "./Icon.svelte";
    import IconConstrain from "./IconConstrain.svelte";

    export let value: number;
    export let step = 1;
    export let min = 1;
    export let max = 9999;
    /**
     * Whether the value is shown as a percentage to the user.
     * It's saved as a proportion.
     */
    export let percentage = false;

    let input: HTMLInputElement;
    let focused = false;
    let multiplier: number;
    $: multiplier = percentage ? 100 : 1;

    /** Set value to a new number, clamping it to a valid range, and
        leaving it unchanged if `newValue` is NaN. */
    function updateValue(newValue: number) {
        if (Number.isNaN(newValue)) {
            // avoid updating the value
        } else {
            value = Math.min(max, Math.max(min, newValue));
        }
        // Assigning to `value` will trigger the stringValue reactive statement below,
        // but Svelte may not redraw the UI. For example, if '1' was shown, and the user
        // enters '0', if the value gets clamped back to '1', Svelte will think the value hasn't
        // changed, and will skip the UI update. So we manually update the DOM to ensure it stays
        // in sync.
        tick().then(() => (input.value = stringValue));
    }

    /**
     * The number of decimal places to record. May be different than the number of decimal places displayed for percentages.
     * @param value The size of the step.
     */
    function decimalPlaces(value: number) {
        if (Math.floor(value) === value) {
            // If the step is an integer, do not show decimal places.
            return 0;
        }
        const places = value.toString().split(".")[1].length || 0;
        const displayedPlace = percentage ? places - 2 : places;
        return Math.max(0, displayedPlace);
    }

    let stringValue: string;
    $: stringValue = (value * multiplier).toFixed(decimalPlaces(step));

    function update(this: HTMLInputElement): void {
        updateValue(parseFloat(this.value) / multiplier);
    }

    function handleWheel(event: WheelEvent) {
        if (focused) {
            updateValue(value + (event.deltaY < 0 ? step : -step));
            event.preventDefault();
        }
    }

    function change(step: number): void {
        updateValue(value + step);
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
        min={min * multiplier}
        max={max * multiplier}
        step={step * multiplier}
        value={stringValue}
        bind:this={input}
        on:blur={update}
        on:focusin={() => (focused = true)}
        on:focusout={() => (focused = false)}
    />
    {#if percentage}
        <div>%</div>
    {/if}
    {#if isDesktop()}
        <!-- svelte-ignore a11y-click-events-have-key-events -->
        <div
            class="spinner decrement"
            class:active={value > min}
            tabindex="-1"
            title={tr.actionsDecrementValue()}
            role="button"
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
                <Icon icon={chevronDown} />
            </IconConstrain>
        </div>
        <!-- svelte-ignore a11y-click-events-have-key-events -->
        <div
            class="spinner increment"
            class:active={value < max}
            tabindex="-1"
            title={tr.actionsIncrementValue()}
            role="button"
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
                <Icon icon={chevronUp} />
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

        &:hover,
        &:focus-within {
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
