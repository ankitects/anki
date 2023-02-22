<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import IconButton from "components/IconButton.svelte";
    import { mdiUnfoldMoreHorizontal } from "image-occlusion/icons";
    import { onDestroy, onMount } from "svelte";

    import {
        colorSwatches,
        getCoordsByHexColor,
        getHueValue,
        getRgbValue,
        rgbaToHexa,
    } from "./lib";

    export let title = "Color Picker";
    export let top = 0;
    export let left = 0;
    export let show = false;
    export let selectedColor = "#000000";
    export let saveColor = (color: string) => {
        color.toString();
    };

    let opacity = 1;
    let rgbValue = "";
    const colors = {
        hex: "",
        rgb: "",
    };

    let showHex = true;
    let clicked = false;
    let inputColorValue = "#000000";
    let colorCoords = { x: 150, y: 10 };

    let canvasRef;
    let colorCanvas;
    let colorCanvasCtx;

    const getMouseCoords = (e, canvas) => {
        const rect = canvas.getBoundingClientRect();
        let mouseCoords = { x: 0, y: 0 },
            x = e.pageX - rect.left,
            y = e.pageY - rect.top;

        if (x < 0) {
            x = 0;
        }
        if (x >= canvas.width) {
            x = canvas.width - 1;
        }

        if (y < 0) {
            y = 0;
        }
        if (y >= canvas.height) {
            y = canvas.height - 1;
        }

        mouseCoords = { x, y };

        if (canvas == colorCanvas) {
            colorCoords = mouseCoords;
        }

        return mouseCoords;
    };

    const selectColor = () => {
        const imageData = colorCanvasCtx.getImageData(
            colorCoords.x,
            colorCoords.y,
            1,
            1,
        ).data;

        rgbValue = `rgba(${imageData[0]}, ${imageData[1]}, ${imageData[2]}, ${opacity})`;
        colors.rgb = rgbValue;
        colors.hex = rgbaToHexa(rgbValue);
        showHex ? (inputColorValue = colors.hex) : (inputColorValue = colors.rgb);
        saveColor(colors.hex);
    };

    const createColorCanvas = (color) => {
        colorCanvasCtx.clearRect(0, 0, colorCanvas.width, colorCanvas.height);
        colorCanvasCtx.fillStyle = color;
        colorCanvasCtx.fillRect(1, 0, colorCanvas.width, colorCanvas.height - 1);
        const whiteGradient = colorCanvasCtx.createLinearGradient(
            1,
            1,
            colorCanvas.width - 1,
            -1,
        );
        whiteGradient.addColorStop(0, "#fff");
        whiteGradient.addColorStop(1, "transparent");
        colorCanvasCtx.fillStyle = whiteGradient;
        colorCanvasCtx.fillRect(0, 0, colorCanvas.width, colorCanvas.height);

        const blackGradient = colorCanvasCtx.createLinearGradient(
            0,
            0,
            -1,
            colorCanvas.height - 1,
        );

        blackGradient.addColorStop(0, "transparent");
        blackGradient.addColorStop(1, "#000");
        colorCanvasCtx.fillStyle = blackGradient;
        colorCanvasCtx.fillRect(0, 1, colorCanvas.width, colorCanvas.height);
    };

    const handleStart = (e) => {
        clicked = true;
        colorCanvasSelect(e, colorCanvas);
    };

    const handleEnd = (e) => {
        clicked = true;
        colorCanvasSelect(e, colorCanvas);
    };

    const handleMove = (e) => {
        if (e.buttons === 1 && clicked) {
            colorCanvasSelect(e, colorCanvas);
        }
    };

    const handleUp = () => {
        clicked = false;
    };

    const colorCanvasSelect = (e, canvas) => {
        getMouseCoords(e, canvas);
        selectColor();
    };

    const colorSliderSelect = (e) => {
        selectedColor = getRgbValue(e.target.value);
        createColorCanvas(selectedColor);
        selectColor();
    };

    const getOpacityBackgroundSvg = () => {
        return encodeURIComponent(`<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 2 2">	
            <path fill="white" d="M1,0H2V1H1V0ZM0,1H1V2H0V1Z"/>	
            <path fill="${selectedColor}" d="M0,0H1V1H0V0ZM1,1H2V2H1V1Z"/>	
            </svg>
        `);
    };

    const opacitySliderSelect = (e) => {
        opacity = parseFloat(e.target.value);
        selectColor();
    };

    const swatchColorSelect = (color) => {
        selectedColor = color;
        const slider = document.querySelector(".hue-slider") as HTMLInputElement;
        slider.value = getHueValue(selectedColor).toString();
        createColorCanvas(selectedColor);
        colorCoords = getCoordsByHexColor(colorCanvas, selectedColor);
        selectColor();
    };

    const initCanvas = (canvas) => {
        if (canvas) {
            colorCanvas = canvas;
            colorCanvasCtx = colorCanvas.getContext("2d", {
                willReadFrequently: true,
            });

            createColorCanvas(selectedColor);
            selectColor();
        }
    };

    onMount(() => {
        if (canvasRef) {
            initCanvas(canvasRef);
        }

        document.addEventListener("mousemove", handleMove);
        document.addEventListener("touchmove", handleMove);
        document.addEventListener("mouseup", handleUp);
    });

    onDestroy(() => {
        document.removeEventListener("mousemove", handleMove);
        document.removeEventListener("touchmove", handleMove);
        document.removeEventListener("mouseup", handleUp);
    });
</script>

<div
    class="color-picker"
    style="top: {top}px; left: {left}px; {show ? '' : 'display:none;'}"
>
    <div class="color-picker-title">
        {title}
    </div>
    <div class="color-picker">
        <div class="color-canvas-container">
            <canvas
                width="232"
                height="136"
                on:mousedown={handleEnd}
                on:touchend={handleEnd}
                bind:this={canvasRef}
            />
            <div
                class="selector"
                style="background: {rgbValue}; left: {colorCoords.x}px; top: {colorCoords.y}px;"
                on:mousedown={handleStart}
                on:touchstart={handleStart}
            />
        </div>
    </div>
    <div class="slider-preview-container">
        <div class="color-preview" style="background: {rgbValue};" />
        <div class="slider-container">
            <div class="color-slider">
                <input
                    type="range"
                    min="0"
                    max="360"
                    value="0"
                    class="hue-slider"
                    on:input={colorSliderSelect}
                />
            </div>
            <div class="color-slider">
                <input
                    type="range"
                    min="0"
                    max="1"
                    value="1"
                    step="0.01"
                    class="opacity-slider"
                    style="background: linear-gradient(to right, transparent, {selectedColor}), url('data:image/svg+xml;utf8, {getOpacityBackgroundSvg()}')"
                    on:input={opacitySliderSelect}
                />
            </div>
        </div>
    </div>
    <div class="color-picker-panel">
        <div class="color-picker-swatches-container">
            <div class="color-picker-swatches">
                {#each colorSwatches as swatchColor}
                    <div
                        class="color-picker-swatch"
                        style="background-color: {swatchColor}"
                        on:click={() => {
                            swatchColorSelect(swatchColor);
                        }}
                    />
                {/each}
            </div>
        </div>
        <div class="result-container">
            <div class="color-input-container">
                <input
                    bind:value={inputColorValue}
                    class="color-input"
                    type="text"
                    spellcheck="false"
                    aria-label="color input field"
                />
                <div class="color-result-label">{showHex ? "HEXA" : "RGBA"}</div>
            </div>
            <IconButton
                class=" color-type-btn"
                iconSize={100}
                on:click={() => {
                    showHex = !showHex;
                    selectColor();
                }}>{@html mdiUnfoldMoreHorizontal}</IconButton
            >
        </div>
    </div>
</div>

<style lang="scss">
    .color-picker {
        display: flex;
        flex-direction: column;
        align-items: center;
        border-radius: 4px;
        font-family: sans-serif;
        user-select: none;
        position: absolute;
        z-index: 100;
        box-shadow: 0 0 10px rgba(0, 0, 0, 0.2) !important;
    }

    .color-picker::before {
        position: absolute;
        content: "";
        top: 0;
        left: 0;
        width: 100%;
        height: 100%;
        background: url('data:image/svg+xml;utf8, <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 2 2"><path fill="white" d="M1,0H2V1H1V0ZM0,1H1V2H0V1Z"/><path fill="gray" d="M0,0H1V1H0V0ZM1,1H2V2H1V1Z"/></svg>');
        background-size: 0.5em;
        border-radius: 0.15em;
        z-index: -1;
    }

    .color-picker-title {
        background: var(--canvas);
        width: 100%;
        text-align: center;
    }

    .color-picker-panel {
        background: var(--canvas);
    }

    .slider-preview-container {
        display: flex;
        flex-wrap: wrap;
        justify-content: space-evenly;
        overflow: hidden;
        margin: auto;
        padding: 6px;
    }

    .slider-container {
        display: flex;
        flex-direction: column;
        justify-content: space-evenly;
        width: 172px;
        height: 40px;
        margin: 6px;
    }

    .color-picker-swatches {
        display: flex;
        flex-wrap: wrap;
        justify-content: center;
        margin: 16px;
        width: 200px;
    }

    .color-picker-swatch {
        width: 24px;
        height: 24px;
        margin: 2px;
        border: 1px solid rgba(0, 0, 0, 0.05);
        border-radius: 4px;
        cursor: pointer;
    }

    input[type="range"]::-webkit-slider-thumb {
        -webkit-appearance: none;
        appearance: none;
        width: 20px;
        height: 20px;
        background: white;
        border-radius: 50%;
        border: 1px solid gray;
        cursor: pointer;
    }

    input[type="range"]::-moz-range-thumb {
        width: 20px;
        height: 20px;
        background: white;
        border-radius: 50%;
        border: 1px solid gray;
        cursor: pointer;
    }

    .color-picker:after {
        display: table;
        content: "";
        clear: both;
    }

    .color-picker > div {
        float: left;
        position: relative;
    }

    .color-canvas-container,
    .color-slider,
    .color-preview {
        cursor: default;
        user-select: none;
    }

    .color-picker canvas {
        display: block;
        user-select: none;
    }

    .selector {
        position: absolute;
        background: #fff;
        top: 0;
        left: 0;
        height: 20px;
        width: 20px;
        border-radius: 100%;
        border: 2px solid #fff;
        box-shadow: 0 0 0 1px rgba(0, 0, 0, 0.1), 1px 1px 3px 1px rgba(0, 0, 0, 0.15);
        transform: translate(-50%, -50%);
    }

    .color-preview {
        width: 36px;
        height: 36px;
        border-radius: 36px;
        box-shadow: 0 0 0 500px var(--canvas) !important;
        border: 1px solid #cecece;
        margin: auto;
    }

    .color-slider {
        position: relative;
        height: 20px;
    }

    .hue-slider {
        -webkit-appearance: none;
        appearance: none;
        width: 100%;
        height: 10px;
        background: linear-gradient(
            to right,
            red,
            yellow,
            green,
            cyan,
            blue,
            magenta,
            red
        );
        border-radius: 10px;
        margin: 0;
    }

    .hue-slider::-webkit-slider-thumb {
        -webkit-appearance: none;
        appearance: none;
        width: 20px;
        height: 20px;
        border-radius: 50%;
        cursor: pointer;
        border: 2px solid white;
        box-shadow: 0px 0px 5px rgba(0, 0, 0, 0.5);
    }

    .opacity-slider {
        -webkit-appearance: none;
        appearance: none;
        width: 100%;
        height: 10px;
        border-radius: 10px;
        margin: 0;
        position: sticky;
    }

    .opacity-slider::-webkit-slider-thumb {
        -webkit-appearance: none;
        appearance: none;
        width: 20px;
        height: 20px;
        border-radius: 50%;
        cursor: pointer;
        border: 2px solid white;
        box-shadow: 0px 0px 5px rgba(0, 0, 0, 0.5);
    }

    .color-input {
        width: 156px;
        border-radius: 4px;
        padding: 6px;
        box-sizing: border-box;
    }

    .color-result-label {
        margin: 2px;
        font-size: 14px;
    }

    .result-container {
        display: flex;
        justify-content: space-evenly;
    }

    .color-input-container {
        display: flex;
        flex-direction: column;
        justify-content: center;
        align-items: center;
    }

    :global(.color-type-btn) {
        width: 36px !important;
        height: 36px !important;
        border-radius: 4px;
        background: var(--canvas);
        cursor: pointer;
        margin: unset;
        padding: unset;
    }
</style>
