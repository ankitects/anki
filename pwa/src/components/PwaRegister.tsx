"use client";

import { useEffect } from "react";

export function PwaRegister() {
  useEffect(() => {
    if (!("serviceWorker" in navigator)) return;

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
