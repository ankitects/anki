import gc
from typing import Callable, List


class CleanupAndExitUseCase:
    """
    Handles graceful application shutdown by notifying registered subscribers and exiting the app.
    """

    def __init__(self):
        self.app = None
        self._subscribers: List[Callable[[], None]] = []

    def subscribe(self, callback: Callable[[], None]):
        """
        Registers a callback to be called before application exit.

        Args:
            callback: A no-argument function to run before exiting.
        """
        self._subscribers.append(callback)

    def unsubscribe(self, callback: Callable[[], None]):
        """
        Unregisters a previously registered callback.

        Args:
            callback: The function to remove from the subscriber list.
        """
        if callback in self._subscribers:
            self._subscribers.remove(callback)

    def __call__(self):
        """
        Executes all registered callbacks, performs cleanup, and exits the app.
        """
        for callback in self._subscribers:
            callback()

        gc.collect()
        self.app._unset_windows_shutdown_block_reason()
        self.app.exit(0)
