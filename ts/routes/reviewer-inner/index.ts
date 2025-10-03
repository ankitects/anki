// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
import "../base.scss";
import "../../reviewer/reviewer.scss";

addEventListener("message", (e) => {
    switch (e.data.type) {
        case "html": {
            document.body.innerHTML = e.data.value;
            break;
        }
        case "nightMode": {
            // This method currently "Flashbangs" the user if they have nightmode on and is a placeholder
            // I will probably use #night-mode in the url.
            const root = document.querySelector("html")!;
            const nightMode = e.data.value;
            if (e.data.value) {
                root.classList.add("night-mode");
            } else {
                root.classList.remove("night-mode");
            }
            document.body.className = nightMode ? "nightMode card" : "card";
            root.className = nightMode ? "night-mode" : "";
            root.setAttribute("data-bs-theme", nightMode ? "dark" : "light");
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
