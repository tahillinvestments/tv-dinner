import { defineConfig } from "tsup"

export default defineConfig({
    entry: ["updater/src/index.ts", "updater/src/serve.ts", "updater/src/matrix.ts"],
    outDir: "dist-updater",
    clean: true,
    format: ["esm"],
    minify: process.env.NODE_ENV !== "development",
    dts: false,
    shims: true
})
