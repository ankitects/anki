<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { chevronDown, chevronUp } from "./icons";

    export let value = 1;
    export let step = 1;
    export let min = 1;
    export let max = 9999;

    let input: HTMLInputElement;
    let clientHeight: number;
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

<div class="spin-box" bind:clientHeight on:wheel={handleWheel}>
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
    <div
        class="spinner"
        style:--half-height="{clientHeight / 2}px"
        on:click={() => input.focus()}
    >
        <div
            class="up"
            disabled={value == max || null}
            on:click={() => {
                if (value < max) value += step;
            }}
        >
            {@html chevronUp}
        </div>
        <div
            class="down"
            disabled={value == min || null}
            on:click={() => {
                if (value > min) value -= step;
            }}
        >
            {@html chevronDown}
        </div>
    </div>
</div>

<style lang="scss">
    @use "sass/button-mixins" as button;
    @use "sass/input-mixins" as input;

    .spin-box {
        width: 100%;
        border: 1px solid var(--border);
        border-radius: var(--border-radius);
        overflow: hidden;
        position: relative;

        input {
            width: 100%;
            padding: 0.2rem 0.75rem;
            border: none;
            outline: none;
            &::-webkit-inner-spin-button {
                display: none;
            }
        }
        &:hover {
            .spinner {
                opacity: 1;
            }
        }
    }
    .spinner {
        opacity: 0;
        cursor: pointer;
        position: absolute;
        inset: 0 0 0 auto;
        border-left: 1px solid var(--border);
        :global(svg) {
            fill: currentColor;
            vertical-align: top;
            height: var(--half-height);
            width: 16px;
        }
        .up,
        .down {
            @include button.base($border: false, $with-active: false);
            width: 100%;
            height: 50%;
        }
        .up {
            border-bottom: 1px solid var(--border);
        }
    }
    :global([dir="rtl"]) {
        .spinner {
            inset: 0 auto 0 0;
            border-left: none;
            border-right: 1px solid var(--border);
        }
    }
</style>
