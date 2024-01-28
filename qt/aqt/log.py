# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

# original from: https://github.com/abdnh/ankiutils/blob/master/src/ankiutils/log.py
# To use inside an addon:
#     from aqt import mw
#     log = mw.addonManager.getLogger(__name__)


from __future__ import annotations
import sys
from typing import Any
from pathlib import Path
import logging
import logging.handlers


class RotatingFileHandler(logging.handlers.RotatingFileHandler):
    MAXSIZE = 3 * 1024 * 1024
    BACKUPCOUNT = 5
    def __init__(self, filename, *args, **kwargs):
        super().__init__(filename, "a", encoding="utf-8", maxBytes=self.MAXSIZE, backupCount=self.BACKUPCOUNT)

    def _on_close(self, _: Any, m: str, *args: Any, **kwargs: Any) -> None:
        breakpoint()
        self.close()


class LoggerManager(logging.Manager):
    TAG = "addon."   # all logs with name addons. will have their content saved to a separate file log

    def __init__(self, path: Path | str, rootnode: logging.Logger):
        super().__init__(rootnode)
        self.path = Path(path)

    # fixme: this seems not called
    def _cleanup(self, handler: RotatingFileHandler):
        from anki.hooks import wrap
        from aqt.addons import AddonManager
        AddonManager.deleteAddon = wrap(  # type: ignore[method-assign]
            AddonManager.deleteAddon, handler._on_close, "before"
        )
        AddonManager.backupUserFiles = wrap(  # type: ignore[method-assign]
            AddonManager.backupUserFiles, handler._on_close, "before"
        )

    def getLogger(self, name: str) -> logging.Logger:
        logger = super().getLogger(name)
        if name.startswith(self.TAG) and RotatingFileHandler not in [handler.__class__ for handler in logger.handlers]:
            path = self.path / f"{name.partition('.')[2]}.log"
            path.parent.mkdir(parents=True, exist_ok=True)
            handler = RotatingFileHandler(path)
            logger.addHandler(handler)
            try:
                # Prevent errors when deleting/updating the add-on on Windows
                self._cleanup(handler)
            except ModuleNotFoundError:
                pass
        return logger


def config(path: Path|str, **kwargs) -> None:
    """configure the mail logging

        path: rootdir to store the addon logs

        Example:
            import aqt.log
            aqt.log.config(
                ProfileManager.get_created_base_folder(opts.base),
                level=logging.DEBUG
            )
    """
    handlers = [
        logging.StreamHandler(stream=sys.stdout),
    ]
    logging.basicConfig(handlers=handlers, **kwargs)
    logging.Logger.manager = LoggerManager(path, logging.root)


def set_level(level: int) -> None:
    """set at runtime the global log level"""
    logging.root.setLevel(level)


if __name__  == "__main__":
    # this will write to xxx
    config("xxx", level=logging.DEBUG)

    logging.root.setLevel(logging.INFO)

    log = logging.getLogger("a")
    log.debug("a debug message")
    log.info("an info message")
    log.warning("a warning message")

    log2 = logging.getLogger("addon.X")
    log2.debug("a debug message")
    log2.info("an info message")
    log2.warning("a warning message")
    log2 = logging.getLogger("addon.X")

