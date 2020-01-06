module.exports = {
  extends: ["eslint:recommended"],
  parserOptions: {
    ecmaVersion: 2019,
    sourceType: "module"
  },
  env: {
    es6: true,
    browser: true
  },
  plugins: ["svelte3"],
  overrides: [
    {
      files: ["**/*.svelte"],
      processor: "svelte3/svelte3"
    }
  ],
  rules: {
    "@typescript-eslint/no-unused-vars": [
      "error",
      { argsIgnorePattern: "^_", varsIgnorePattern: "^_" }
    ],
    "no-unused-vars": [
      "error",
      { argsIgnorePattern: "^_", varsIgnorePattern: "^_" }
    ]
  },
  settings: {}
};
