// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
import "../base.scss";
import "../../reviewer/reviewer.scss";
import { enableNightMode } from "../reviewer/reviewer";

const urlParams = new URLSearchParams(location.search);

const style = document.createElement("style");
document.head.appendChild(style);

addEventListener("message", (e) => {
    switch (e.data.type) {
        case "html": {
            document.body.innerHTML = e.data.value;
            if (e.data.css) {
                style.innerHTML = e.data.css;
            }
            if (e.data.bodyclass) {
                document.body.className = e.data.bodyclass;
                const theme = urlParams.get("nightMode");
                if (theme !== null) {
                    enableNightMode();
                    document.body.classList.add("night_mode");
                    document.body.classList.add("nightMode");
                }
            }
            break;
        }
        default: {
            console.warn(`Unknown message type: ${e.data.type}`);
            break;
        }
    }
});

const base = document.createElement("base");
base.href = "/";
document.head.appendChild(base);

function pycmd(cmd: string) {
    window.parent.postMessage({ type: "pycmd", value: cmd }, "*");
}
globalThis.pycmd = pycmd;
