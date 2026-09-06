"use client";

import { useEffect } from "react";

export function PwaRegister() {
  useEffect(() => {
    if (!("serviceWorker" in navigator)) return;

    if (process.env.NODE_ENV !== "production") {
      // A previously installed production worker can otherwise serve stale
      // client bundles while developing on the same localhost origin.
      const removeDevelopmentWorker = async () => {
        const registrations = await navigator.serviceWorker.getRegistrations();
        await Promise.all(registrations
          .filter((registration) => registration.scope.startsWith(window.location.origin))
          .map((registration) => registration.unregister()));
        const cacheNames = await caches.keys();
        await Promise.all(cacheNames
          .filter((name) => name.startsWith("anki-pwa-shell-"))
          .map((name) => caches.delete(name)));
      };
      void removeDevelopmentWorker();
      return;
    }

    const register = async () => {
      try {
        await navigator.serviceWorker.register("/sw.js", { scope: "/" });
        if (navigator.storage?.persist) {
          await navigator.storage.persist();
        }
      } catch (error) {
        console.error("PWA registration failed", error);
      }
    };

    void register();
  }, []);

  return null;
}
