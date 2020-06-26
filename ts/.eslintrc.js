module.exports = {
    extends: [
        "eslint:recommended",
        "plugin:@typescript-eslint/eslint-recommended",
        "plugin:@typescript-eslint/recommended",
    ],
    parser: "@typescript-eslint/parser",
    plugins: ["@typescript-eslint", "svelte3"],
    rules: {
        "prefer-const": "warn",
        "@typescript-eslint/ban-ts-ignore": "warn",
        "@typescript-eslint/no-unused-vars": [
            "warn",
            { argsIgnorePattern: "^_", varsIgnorePattern: "^_" },
        ],
    },
    overrides: [
        {
            files: ["*.svelte"],
            processor: "svelte3/svelte3",
            rules: {
                "@typescript-eslint/explicit-function-return-type": "off",
            },
        },
    ],
    env: { browser: true },
};
