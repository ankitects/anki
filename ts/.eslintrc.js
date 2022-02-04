module.exports = {
    extends: ["eslint:recommended", "plugin:compat/recommended"],
    parser: "@typescript-eslint/parser",
    plugins: [
        "svelte3",
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
            },
        },
        {
            files: "**/*.svelte",
            processor: "svelte3/svelte3",
            rules: {
                "no-redeclare": "off",
                "no-global-assign": "off",
            },
        },
    ],
    env: { browser: true },
    ignorePatterns: ["backend_proto.d.ts", "*.svelte.d.ts"],
    globals: {
        globalThis: false,
        NodeListOf: false,
    },
    settings: {
        "svelte3/typescript": () => require("typescript"),
    },
};
