// vite.config.ts
import svg from "file:///home/anon/Downloads/anki/Source_Code/anki/out/node_modules/@poppanator/sveltekit-svg/dist/index.js";
import { sveltekit } from "file:///home/anon/Downloads/anki/Source_Code/anki/out/node_modules/@sveltejs/kit/src/exports/vite/index.js";
import { realpathSync } from "fs";
import { defineConfig as defineViteConfig, mergeConfig } from "file:///home/anon/Downloads/anki/Source_Code/anki/out/node_modules/vite/dist/node/index.js";
import { defineConfig as defineVitestConfig } from "file:///home/anon/Downloads/anki/Source_Code/anki/out/node_modules/vitest/dist/config.js";
var configure = (proxy, _options) => {
  proxy.on("error", (err) => {
    console.log("proxy error", err);
  });
  proxy.on("proxyReq", (proxyReq, req) => {
    console.log("Sending Request to the Target:", req.method, req.url);
  });
  proxy.on("proxyRes", (proxyRes, req) => {
    console.log("Received Response from the Target:", proxyRes.statusCode, req.url);
  });
};
var viteConfig = defineViteConfig({
  plugins: [sveltekit(), svg({})],
  build: {
    reportCompressedSize: false,
    // defaults use chrome87, but we need 77 for qt 5.14
    target: ["es2020", "edge88", "firefox78", "chrome77", "safari14"]
  },
  server: {
    host: "127.0.0.1",
    fs: {
      // Allow serving files project root and out dir
      allow: [
        // realpathSync(".."),
        // "/home/dae/Local/build/anki/node_modules",
        realpathSync("../out")
        // realpathSync("../out/node_modules"),
      ]
    },
    proxy: {
      "/_anki": {
        target: "http://127.0.0.1:40000",
        changeOrigin: true,
        autoRewrite: true,
        configure
      }
    }
  }
});
var vitestConfig = defineVitestConfig({
  test: {
    include: ["**/*.{test,spec}.{js,ts}"],
    cache: {
      // prevent vitest from creating ts/node_modules/.vitest
      dir: "../node_modules/.vitest"
    }
  }
});
var vite_config_default = mergeConfig(viteConfig, vitestConfig);
export {
  vite_config_default as default
};
//# sourceMappingURL=data:application/json;base64,ewogICJ2ZXJzaW9uIjogMywKICAic291cmNlcyI6IFsidml0ZS5jb25maWcudHMiXSwKICAic291cmNlc0NvbnRlbnQiOiBbImNvbnN0IF9fdml0ZV9pbmplY3RlZF9vcmlnaW5hbF9kaXJuYW1lID0gXCIvaG9tZS9hbm9uL0Rvd25sb2Fkcy9hbmtpL1NvdXJjZV9Db2RlL2Fua2kvdHNcIjtjb25zdCBfX3ZpdGVfaW5qZWN0ZWRfb3JpZ2luYWxfZmlsZW5hbWUgPSBcIi9ob21lL2Fub24vRG93bmxvYWRzL2Fua2kvU291cmNlX0NvZGUvYW5raS90cy92aXRlLmNvbmZpZy50c1wiO2NvbnN0IF9fdml0ZV9pbmplY3RlZF9vcmlnaW5hbF9pbXBvcnRfbWV0YV91cmwgPSBcImZpbGU6Ly8vaG9tZS9hbm9uL0Rvd25sb2Fkcy9hbmtpL1NvdXJjZV9Db2RlL2Fua2kvdHMvdml0ZS5jb25maWcudHNcIjsvLyBDb3B5cmlnaHQ6IEFua2l0ZWN0cyBQdHkgTHRkIGFuZCBjb250cmlidXRvcnNcbi8vIExpY2Vuc2U6IEdOVSBBR1BMLCB2ZXJzaW9uIDMgb3IgbGF0ZXI7IGh0dHA6Ly93d3cuZ251Lm9yZy9saWNlbnNlcy9hZ3BsLmh0bWxcbmltcG9ydCBzdmcgZnJvbSBcIkBwb3BwYW5hdG9yL3N2ZWx0ZWtpdC1zdmdcIjtcbmltcG9ydCB7IHN2ZWx0ZWtpdCB9IGZyb20gXCJAc3ZlbHRlanMva2l0L3ZpdGVcIjtcbmltcG9ydCB7IHJlYWxwYXRoU3luYyB9IGZyb20gXCJmc1wiO1xuaW1wb3J0IHsgZGVmaW5lQ29uZmlnIGFzIGRlZmluZVZpdGVDb25maWcsIG1lcmdlQ29uZmlnIH0gZnJvbSBcInZpdGVcIjtcbmltcG9ydCB7IGRlZmluZUNvbmZpZyBhcyBkZWZpbmVWaXRlc3RDb25maWcgfSBmcm9tIFwidml0ZXN0L2NvbmZpZ1wiO1xuXG5jb25zdCBjb25maWd1cmUgPSAocHJveHk6IGFueSwgX29wdGlvbnM6IGFueSkgPT4ge1xuICAgIHByb3h5Lm9uKFwiZXJyb3JcIiwgKGVycjogYW55KSA9PiB7XG4gICAgICAgIGNvbnNvbGUubG9nKFwicHJveHkgZXJyb3JcIiwgZXJyKTtcbiAgICB9KTtcbiAgICBwcm94eS5vbihcInByb3h5UmVxXCIsIChwcm94eVJlcTogYW55LCByZXE6IGFueSkgPT4ge1xuICAgICAgICBjb25zb2xlLmxvZyhcIlNlbmRpbmcgUmVxdWVzdCB0byB0aGUgVGFyZ2V0OlwiLCByZXEubWV0aG9kLCByZXEudXJsKTtcbiAgICB9KTtcbiAgICBwcm94eS5vbihcInByb3h5UmVzXCIsIChwcm94eVJlczogYW55LCByZXE6IGFueSkgPT4ge1xuICAgICAgICBjb25zb2xlLmxvZyhcIlJlY2VpdmVkIFJlc3BvbnNlIGZyb20gdGhlIFRhcmdldDpcIiwgcHJveHlSZXMuc3RhdHVzQ29kZSwgcmVxLnVybCk7XG4gICAgfSk7XG59O1xuXG5jb25zdCB2aXRlQ29uZmlnID0gZGVmaW5lVml0ZUNvbmZpZyh7XG4gICAgcGx1Z2luczogW3N2ZWx0ZWtpdCgpLCBzdmcoe30pXSxcbiAgICBidWlsZDoge1xuICAgICAgICByZXBvcnRDb21wcmVzc2VkU2l6ZTogZmFsc2UsXG4gICAgICAgIC8vIGRlZmF1bHRzIHVzZSBjaHJvbWU4NywgYnV0IHdlIG5lZWQgNzcgZm9yIHF0IDUuMTRcbiAgICAgICAgdGFyZ2V0OiBbXCJlczIwMjBcIiwgXCJlZGdlODhcIiwgXCJmaXJlZm94NzhcIiwgXCJjaHJvbWU3N1wiLCBcInNhZmFyaTE0XCJdLFxuICAgIH0sXG4gICAgc2VydmVyOiB7XG4gICAgICAgIGhvc3Q6IFwiMTI3LjAuMC4xXCIsXG4gICAgICAgIGZzOiB7XG4gICAgICAgICAgICAvLyBBbGxvdyBzZXJ2aW5nIGZpbGVzIHByb2plY3Qgcm9vdCBhbmQgb3V0IGRpclxuICAgICAgICAgICAgYWxsb3c6IFtcbiAgICAgICAgICAgICAgICAvLyByZWFscGF0aFN5bmMoXCIuLlwiKSxcbiAgICAgICAgICAgICAgICAvLyBcIi9ob21lL2RhZS9Mb2NhbC9idWlsZC9hbmtpL25vZGVfbW9kdWxlc1wiLFxuICAgICAgICAgICAgICAgIHJlYWxwYXRoU3luYyhcIi4uL291dFwiKSxcbiAgICAgICAgICAgICAgICAvLyByZWFscGF0aFN5bmMoXCIuLi9vdXQvbm9kZV9tb2R1bGVzXCIpLFxuICAgICAgICAgICAgXSxcbiAgICAgICAgfSxcbiAgICAgICAgcHJveHk6IHtcbiAgICAgICAgICAgIFwiL19hbmtpXCI6IHtcbiAgICAgICAgICAgICAgICB0YXJnZXQ6IFwiaHR0cDovLzEyNy4wLjAuMTo0MDAwMFwiLFxuICAgICAgICAgICAgICAgIGNoYW5nZU9yaWdpbjogdHJ1ZSxcbiAgICAgICAgICAgICAgICBhdXRvUmV3cml0ZTogdHJ1ZSxcbiAgICAgICAgICAgICAgICBjb25maWd1cmUsXG4gICAgICAgICAgICB9LFxuICAgICAgICB9LFxuICAgIH0sXG59KTtcblxuY29uc3Qgdml0ZXN0Q29uZmlnID0gZGVmaW5lVml0ZXN0Q29uZmlnKHtcbiAgICB0ZXN0OiB7XG4gICAgICAgIGluY2x1ZGU6IFtcIioqLyoue3Rlc3Qsc3BlY30ue2pzLHRzfVwiXSxcbiAgICAgICAgY2FjaGU6IHtcbiAgICAgICAgICAgIC8vIHByZXZlbnQgdml0ZXN0IGZyb20gY3JlYXRpbmcgdHMvbm9kZV9tb2R1bGVzLy52aXRlc3RcbiAgICAgICAgICAgIGRpcjogXCIuLi9ub2RlX21vZHVsZXMvLnZpdGVzdFwiLFxuICAgICAgICB9LFxuICAgIH0sXG59KTtcblxuZXhwb3J0IGRlZmF1bHQgbWVyZ2VDb25maWcodml0ZUNvbmZpZywgdml0ZXN0Q29uZmlnKTtcbiJdLAogICJtYXBwaW5ncyI6ICI7QUFFQSxPQUFPLFNBQVM7QUFDaEIsU0FBUyxpQkFBaUI7QUFDMUIsU0FBUyxvQkFBb0I7QUFDN0IsU0FBUyxnQkFBZ0Isa0JBQWtCLG1CQUFtQjtBQUM5RCxTQUFTLGdCQUFnQiwwQkFBMEI7QUFFbkQsSUFBTSxZQUFZLENBQUMsT0FBWSxhQUFrQjtBQUM3QyxRQUFNLEdBQUcsU0FBUyxDQUFDLFFBQWE7QUFDNUIsWUFBUSxJQUFJLGVBQWUsR0FBRztBQUFBLEVBQ2xDLENBQUM7QUFDRCxRQUFNLEdBQUcsWUFBWSxDQUFDLFVBQWUsUUFBYTtBQUM5QyxZQUFRLElBQUksa0NBQWtDLElBQUksUUFBUSxJQUFJLEdBQUc7QUFBQSxFQUNyRSxDQUFDO0FBQ0QsUUFBTSxHQUFHLFlBQVksQ0FBQyxVQUFlLFFBQWE7QUFDOUMsWUFBUSxJQUFJLHNDQUFzQyxTQUFTLFlBQVksSUFBSSxHQUFHO0FBQUEsRUFDbEYsQ0FBQztBQUNMO0FBRUEsSUFBTSxhQUFhLGlCQUFpQjtBQUFBLEVBQ2hDLFNBQVMsQ0FBQyxVQUFVLEdBQUcsSUFBSSxDQUFDLENBQUMsQ0FBQztBQUFBLEVBQzlCLE9BQU87QUFBQSxJQUNILHNCQUFzQjtBQUFBO0FBQUEsSUFFdEIsUUFBUSxDQUFDLFVBQVUsVUFBVSxhQUFhLFlBQVksVUFBVTtBQUFBLEVBQ3BFO0FBQUEsRUFDQSxRQUFRO0FBQUEsSUFDSixNQUFNO0FBQUEsSUFDTixJQUFJO0FBQUE7QUFBQSxNQUVBLE9BQU87QUFBQTtBQUFBO0FBQUEsUUFHSCxhQUFhLFFBQVE7QUFBQTtBQUFBLE1BRXpCO0FBQUEsSUFDSjtBQUFBLElBQ0EsT0FBTztBQUFBLE1BQ0gsVUFBVTtBQUFBLFFBQ04sUUFBUTtBQUFBLFFBQ1IsY0FBYztBQUFBLFFBQ2QsYUFBYTtBQUFBLFFBQ2I7QUFBQSxNQUNKO0FBQUEsSUFDSjtBQUFBLEVBQ0o7QUFDSixDQUFDO0FBRUQsSUFBTSxlQUFlLG1CQUFtQjtBQUFBLEVBQ3BDLE1BQU07QUFBQSxJQUNGLFNBQVMsQ0FBQywwQkFBMEI7QUFBQSxJQUNwQyxPQUFPO0FBQUE7QUFBQSxNQUVILEtBQUs7QUFBQSxJQUNUO0FBQUEsRUFDSjtBQUNKLENBQUM7QUFFRCxJQUFPLHNCQUFRLFlBQVksWUFBWSxZQUFZOyIsCiAgIm5hbWVzIjogW10KfQo=
