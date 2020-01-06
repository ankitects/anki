import React from "react";
import ReactDOM from "react-dom";
import "./ui/index.css";
import App from "./ui/App";
import * as serviceWorker from "./serviceWorker";

ReactDOM.render(<App />, document.getElementById("root"));

serviceWorker.unregister();
