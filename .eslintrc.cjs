module.exports = {
    root: true,
    extends: ["eslint:recommended", "plugin:compat/recommended", "plugin:svelte/recommended"],
    parser: "@typescript-eslint/parser",
    parserOptions: {
        extraFileExtensions: [".svelte"],
    },
    plugins: [
        "import",
        "@typescript-eslint",
        "@typescript-eslint/eslint-plugin",
    ],
    rules: {
        "@typescript-eslint/ban-ts-comment": "warn",
        "@typescript-eslint/no-unused-vars": [
            "warn",
            { argsIgnorePattern: "^_", varsIgnorePattern: "^_" },
        ],
        "no-unused-vars": ["warn", { argsIgnorePattern: "^_" }],
        "import/newline-after-import": "warn",
        "import/no-useless-path-segments": "warn",
        "prefer-const": "warn",
        "no-nested-ternary": "warn",
        "curly": "error",
        "@typescript-eslint/consistent-type-imports": "error",
    },
    overrides: [
        {
            files: "**/*.ts",
            extends: [
                "plugin:@typescript-eslint/eslint-recommended",
                "plugin:@typescript-eslint/recommended",
            ],
            rules: {
                "@typescript-eslint/no-non-null-assertion": "off",
                "@typescript-eslint/no-explicit-any": "off",
            },
        },
        {
            files: ["*.svelte"],
            parser: "svelte-eslint-parser",
            parserOptions: {
                parser: "@typescript-eslint/parser",
            },
            rules: {
                "svelte/no-at-html-tags": "off",
                "svelte/valid-compile": ["error", { "ignoreWarnings": true }],
                "@typescript-eslint/no-explicit-any": "off",
                "prefer-const": "off",
                // TODO: enable this when we update to eslint-plugin-svelte 3
                // "svelte/prefer-const": "warn",
            },
        },
    ],
    env: { browser: true, es2020: true },
    ignorePatterns: ["backend_proto.d.ts", "*.svelte.d.ts", "vendor", "extra/*", "vite.config.ts", "hooks.client.js"],
    globals: {
        globalThis: false,
        NodeListOf: false,
        $$Generic: "readonly",
    },
};
