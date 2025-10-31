<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import "./base.scss";
    import { warn, debug, info, error } from "@tauri-apps/plugin-log";

    function forwardConsole(
        fnName: "log" | "debug" | "info" | "warn" | "error",
        logger: (message: string) => Promise<void>,
    ) {
        const original = console[fnName];
        console[fnName] = (message: any) => {
            original(message);
            logger(message);
        };
    }

    forwardConsole("debug", debug);
    forwardConsole("info", info);
    forwardConsole("warn", warn);
    forwardConsole("error", error);
</script>

<slot />
