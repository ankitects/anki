const sveltePreprocess = require("svelte-preprocess");

module.exports = {
    preprocess: sveltePreprocess({
        typescript: { compilerOptions: { declaration: false, outDir: null } },
    }),
};
