<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { on } from "@tslib/events";
    import { onMount } from "svelte";
    import { protoBase64 } from "@bufbuild/protobuf";
    import { listen } from "@tauri-apps/api/event";
    import { Terminal } from "@xterm/xterm";
    import { WebglAddon } from "@xterm/addon-webgl";
    import "@xterm/xterm/css/xterm.css";

    let {
        term = $bindable(),
        open = $bindable(false),
    }: { term: Terminal | undefined; open: boolean } = $props();

    let termRef: HTMLDivElement;

    onMount(() => {
        term = new Terminal({
            fontFamily: '"Cascadia Code", Menlo, monospace',
            disableStdin: true,
            rows: 10,
            cols: 50,
            cursorStyle: "underline",
            cursorInactiveStyle: "none",
            altClickMovesCursor: false,
            // TODO: saw this in the docs, but do we need it?
            // windowsMode: navigator.platform.indexOf("Win") != -1,
        });
        // term.options.

        term.open(termRef);

        // dom renderer has viewport issues, try webgl
        try {
            const webgl = new WebglAddon();
            term.loadAddon(webgl);
        } catch (e) {
            console.log("WebGL addon threw an exception during load", e);
        }

        const unlisten = listen<string>("pty-data", (e) => {
            const data = protoBase64.dec(e.payload);
            open = true;
            term!.write(data);
        });

        // prevent wheel events from scrolling page if terminal has scrollback
        const unsub = on(
            document.querySelector(".xterm")! as HTMLElement,
            "wheel",
            (e) => {
                if (term && term.buffer.active.baseY > 0) {
                    e.preventDefault();
                }
            },
        );

        return () => {
            unlisten.then((cb) => cb());
            unsub();
            term!.dispose();
        };
    });
</script>

<div class="term-container centre" style:display={open ? "block" : "none"}>
    <div class="term" bind:this={termRef}></div>
</div>

<style lang="scss">
    .term-container {
        display: flex;
        margin: 20px 0 50px;
        background-color: black;
        border-radius: var(--border-radius-medium);
    }

    .term {
        padding: 10px 20px;
    }
</style>
