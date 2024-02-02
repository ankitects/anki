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

import logging
import sys
from logging import Logger
from logging.handlers import TimedRotatingFileHandler
from pathlib import Path

# All loggers with the following prefix will be treated as add-on loggers
ADDON_LOGGER_PREFIX = "addon."

# Formatter used for all loggers
FORMATTER = logging.Formatter("%(asctime)s:%(levelname)s:%(name)s: %(message)s")


class AnkiLoggerManager(logging.Manager):
    def __init__(
        self,
        logs_path: Path | str,
        existing_loggers: dict[str, Logger],
        rootnode: logging.Logger,
    ):
        super().__init__(rootnode)
        self.loggerDict = existing_loggers
        self.logs_path = Path(logs_path)

    def getLogger(self, name: str) -> logging.Logger:
        if not name.startswith(ADDON_LOGGER_PREFIX) or name in self.loggerDict:
            return super().getLogger(name)

        # Create a new add-on logger
        logger = super().getLogger(name)

        module = name.split(ADDON_LOGGER_PREFIX)[1].partition(".")[0]
        path = self.logs_path / "addons" / module / f"{module}.log"
        path.parent.mkdir(parents=True, exist_ok=True)

        # Keep the last 10 days of logs
        handler = TimedRotatingFileHandler(
            filename=path, when="D", interval=1, backupCount=10, encoding="utf-8"
        )
        handler.setFormatter(FORMATTER)

        logger.addHandler(handler)

        return logger


def find_addon_logger(module: str) -> logging.Logger | None:
    return logging.root.manager.loggerDict.get(f"{ADDON_LOGGER_PREFIX}{module}")


def setup_logging(path: Path, **kwargs) -> None:
    """
    Set up logging for the application.

    Configures the root logger to output logs to stdout by default, with custom
    handling for add-on logs. The add-on logs are saved to a separate folder and file
    for each add-on, under the path provided.

    Args:
        path (Path): The path where the log files should be stored.
        **kwargs: Arbitrary keyword arguments for logging.basicConfig
    """

    # Patch root logger manager to handle add-on loggers
    logger_manager = AnkiLoggerManager(
        path, existing_loggers=logging.root.manager.loggerDict, rootnode=logging.root
    )
    logging.Logger.manager = logger_manager

    stdout_handler = logging.StreamHandler(stream=sys.stdout)
    stdout_handler.setFormatter(FORMATTER)
    logging.basicConfig(handlers=[stdout_handler], **kwargs)
    logging.captureWarnings(True)

    # Silence some loggers of external libraries:
    silenced_loggers = [
        "waitress.queue",
    ]
    for logger in silenced_loggers:
        logging.getLogger(logger).setLevel(logging.CRITICAL)
        logging.getLogger(logger).propagate = False


if __name__ == "__main__":
    # this will write to deleteme
    setup_logging("deleteme", format=logging.BASIC_FORMAT, level=logging.DEBUG)

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
