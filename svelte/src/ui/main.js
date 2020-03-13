import App from "./App.svelte";

const app = new App({
  target: document.body
});

window.onerror = function(e) {
  window.alert(`An error occurred: ${e}`);
};
window.onunhandledrejection = function(e) {
  window.alert(`An error occurred: ${e.reason}`);
};

export default app;
