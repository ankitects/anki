const HtmlWebpackPlugin = require("html-webpack-plugin");
const mode = process.env.NODE_ENV || "development";
const prod = mode === "production";
var path = require("path");

module.exports = {
    entry: {
        graphs: ["./src/stats/graphs-bootstrap.ts"],
        congrats: ["./src/sched/congrats-bootstrap.ts"],
    },
    output: {
        library: "anki",
    },
    plugins: [
        new HtmlWebpackPlugin({
            filename: "graphs.html",
            chunks: ["graphs"],
            template: "src/html/graphs.html",
        }),
        new HtmlWebpackPlugin({
            filename: "congrats.html",
            chunks: ["congrats"],
            template: "src/html/congrats.html",
        }),
    ],
    externals: {
        moment: "moment",
    },
    devServer: {
        contentBase: "./dist",
        port: 9000,
        // host: "0.0.0.0",
        // disableHostCheck: true,
        proxy: {
            "/_anki": {
                target: "http://localhost:9001",
            },
        },
    },
    resolve: {
        alias: {
            svelte: path.resolve("node_modules", "svelte"),
        },
        extensions: [".mjs", ".js", ".svelte", ".ts", ".tsx"],
        mainFields: ["svelte", "browser", "module", "main"],
    },
    module: {
        rules: [
            {
                test: /\.s?css$/i,
                use: ["style-loader", "css-loader", "sass-loader"],
            },
            {
                test: /\.(svelte)$/,
                exclude: /node_modules/,
                use: [
                    {
                        loader: "svelte-loader",
                        options: {
                            emitCss: true,
                            preprocess: require("svelte-preprocess")({
                                typescript: {
                                    transpileOnly: true,
                                    compilerOptions: {
                                        declaration: false,
                                    },
                                },
                            }),
                        },
                    },
                ],
            },
            {
                test: /\.tsx?$/,
                use: ["ts-loader"],
                exclude: /node_modules/,
            },
            {
                test: /\.(tsx?|js)$/,
                loader: "eslint-loader",
                exclude: /node_modules/,
                options: {
                    fix: true,
                },
            },
        ],
    },
    mode,
    devtool: prod ? false : "source-map",
    optimization: {
        splitChunks: {
            // chunks: "all",
        },
    },
    performance: {
        hints: false,
        maxEntrypointSize: 512000,
        maxAssetSize: 512000,
    },
};
