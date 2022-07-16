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
        "plugin:prettier/recommended",
    ],
    parser: "@typescript-eslint/parser",
    parserOptions: {
        extraFileExtensions: [".svelte"],
    },
    plugins: [
        "svelte",
        "prettier",
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
        "prettier/prettier": "warn",
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
                "no-redeclare": "off",
                "no-global-assign": "off",
                "no-self-assign": "off",
                "svelte/no-at-html-tags": "off",
                /* We would also ideally get rid of this: */
                "@typescript-eslint/no-explicit-any": "off",
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
