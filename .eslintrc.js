module.exports = {
    root: true,
    extends: [
        "eslint:recommended",
        "plugin:compat/recommended",
        "plugin:svelte/base",
        "plugin:svelte/recommended",
        "plugin:svelte/prettier",
        "plugin:@typescript-eslint/recommended",
        "plugin:@typescript-eslint/eslint-recommended",
        "prettier",
    ],
    parser: "@typescript-eslint/parser",
    parserOptions: {
        extraFileExtensions: [".svelte"],
    },
    plugins: [
        "svelte",
        "import",
        "simple-import-sort",
        "@typescript-eslint",
        "@typescript-eslint/eslint-plugin",
    ],
    rules: {
        "@typescript-eslint/ban-ts-comment": "warn",
        "@typescript-eslint/no-unused-vars": [
            "warn",
            { argsIgnorePattern: "^_", varsIgnorePattern: "^_" },
        ],
        "import/newline-after-import": "warn",
        "import/no-useless-path-segments": "warn",
        "simple-import-sort/imports": "warn",
        "simple-import-sort/exports": "warn",
        "prefer-const": "warn",
        "no-nested-ternary": "warn",
        "@typescript-eslint/no-non-null-assertion": "off",
    },
    overrides: [
        {
            files: "*.svelte",
            parser: "svelte-eslint-parser",
            parserOptions: {
                parser: "@typescript-eslint/parser",
            },
            rules: {
                "svelte/no-useless-mustaches": "warn",
                "svelte/require-optimized-style-attribute": "warn",
                "svelte/html-quotes": "warn",
                "svelte/prefer-class-directive": "warn",
                "svelte/prefer-style-directive": "warn",
                "svelte/shorthand-attribute": "warn",
                "svelte/shorthand-directive": "warn",
                "no-redeclare": "off",
                "no-global-assign": "off",
                "no-self-assign": "off",
                "svelte/no-at-html-tags": "off",
                /* We would also ideally get rid of this: */
                "@typescript-eslint/no-explicit-any": "off",
                /* Does not recognize that `store` and `$store` belongs to the same var: */
                "@typescript-eslint/no-unused-vars": "off",
                "no-undef": "off",
            },
        },
    ],
    env: { browser: true },
    ignorePatterns: ["backend_proto.d.ts", "*.svelte.d.ts"],
    globals: {
        globalThis: false,
        NodeListOf: false,
    },
};
