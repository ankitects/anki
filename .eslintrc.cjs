const innerHTMLRestriction = {
    selector: "AssignmentExpression > MemberExpression.left[property.name=/^(innerHTML|outerHTML)$/]",
    message:
        "Don't assign to innerHTML/outerHTML directly; use setInnerHTML()/setOuterHTML() with a TrustedHTML value.",
};

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
        "no-restricted-syntax": ["error", innerHTMLRestriction],
    },
    overrides: [
        {
            files: ["**/*.test.ts", "ts/tests/**", "qt/aqt/data/web/js/**"],
            rules: {
                "no-restricted-syntax": "off",
            },
        },
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
                svelteFeatures: {
                    experimentalGenerics: true,
                },
            },
            rules: {
                "svelte/no-at-html-tags": "off",
                "no-restricted-syntax": [
                    "error",
                    innerHTMLRestriction,
                    {
                        selector:
                            "SvelteMustacheTag[kind=\"raw\"]:not([expression.type=\"CallExpression\"][expression.callee.name=/^(trustedHTML|untrustedHTML)$/])",
                        message:
                            "{@html} is only allowed with a TrustedHTML helper (e.g., trustedHTML, untrustedHTML).",
                    },
                ],
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
