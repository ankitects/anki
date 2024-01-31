# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

# original from: https://github.com/abdnh/ankiutils/blob/master/src/ankiutils/log.py
# To use inside an addon:
#     from aqt import mw
#     log = mw.addonManager.getLogger(__name__)
#     log.info("Hello world")
#
# The "Hello world" message will be written under:
#       pm.addonFolder() / <ADDON-ID> / "user_files" / "logs" / "<ADDON-ID>.log"

from __future__ import annotations
import sys
import functools
from pathlib import Path
import logging
import logging.handlers
from typing import Any


# this formatter instance is the same for all the handlers
FORMATTER = logging.Formatter("%(asctime)s:%(levelname)s:%(name)s: %(message)s")


class RotatingFileHandler(logging.handlers.RotatingFileHandler):
    # The addon logs will be rotated once they reach MAXSIZE, to a total of BACKUPCOUNT
    MAXSIZE = 3 * 1024 * 1024
    BACKUPCOUNT = 5

    def __init__(self, filename: Path, *args: Any, **kwargs: Any):
        super().__init__(
            filename,
            "a",
            encoding="utf-8",
            maxBytes=self.MAXSIZE,
            backupCount=self.BACKUPCOUNT,
        )


class LoggerManager(logging.Manager):
    TAG = "addon."  # all logs with name addons. will have their content saved to a separate file log

    def __init__(self, path: Path | str, rootnode: logging.Logger):
        super().__init__(rootnode)
        self.path = Path(path)

    def getLogger(self, name: str) -> logging.Logger:
        logger = super().getLogger(name)
        if name.startswith(self.TAG) and RotatingFileHandler not in [
            handler.__class__ for handler in logger.handlers
        ]:
            module = name[len(self.TAG) :].partition(".")[0]
            path = self.path / module / "user_files" / "logs" / f"{module}.log"
            path.parent.mkdir(parents=True, exist_ok=True)

            handler = RotatingFileHandler(path)
            logger.addHandler(handler)
            handler.setFormatter(FORMATTER)
        return logger


def config(path: Path | str, **kwargs) -> None:
    """configure the mail logging

    path: rootdir to store the addon logs

    Example:
        import aqt.log
        aqt.log.config(
            pm.addonFolder(),
            level=logging.DEBUG
        )
    """
    
    # we save the logs already defined
    old = logging.root.manager.loggerDict

    handlers = [
        logging.StreamHandler(stream=sys.stdout),
    ]
    handlers[0].setFormatter(FORMATTER)
    logging.basicConfig(handlers=handlers, **kwargs)
    logging.Logger.manager = LoggerManager(path, logging.root)
    logging.root.manager.loggerDict.update(old)

    logging.captureWarnings(True)

    # silence these loggers:
    loggers = [
        "waitress.queue",
    ]
    for logger in loggers:
        logging.getLogger(logger).setLevel(logging.CRITICAL)
        logging.getLogger(logger).propagate = False


def close_module(module: str, reopen: bool = False) -> None:
    """close the RotatingFileHandler handler"""
    logger = logging.getLogger(f"{LoggerManager.TAG}{module}")
    logger.debug("closing handler on %s", module)

    found = None
    for index, handler in enumerate(logger.handlers):
        if isinstance(handler, RotatingFileHandler):
            found = (index, handler)
            break

    if found:
        index, handler = found
        handler.close()
        if reopen:
            del logger.handlers[index]
            logging.Logger.manager.getLogger(f"{LoggerManager.TAG}{module}")


if __name__ == "__main__":
    # this will write to deleteme
    config("deleteme", format=logging.BASIC_FORMAT, level=logging.DEBUG)

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
