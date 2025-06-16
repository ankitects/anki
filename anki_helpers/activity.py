import time
from collections import defaultdict
from typing import DefaultDict
def analyze_activity(col, days=30):
    day_cutoff = col.sched.day_cutoff
    start_time = day_cutoff - days * 86400
    start_ts = start_time * 1000  # ms

    rows = col.db.all(
        """
        SELECT id FROM revlog
        WHERE id > ?
        """,
        start_ts,
    )

    days_dict = defaultdict(int)
    for (id,) in rows:
        day = int((id / 1000) // 86400)
        days_dict[day] += 1

    total_days = len(days_dict)
    average_per_day = sum(days_dict.values()) / days
    low_days = sum(1 for v in days_dict.values() if v < 10)  # z. B. Schwelle

    return {
        "active_days": total_days,
        "average_per_day": round(average_per_day, 2),
        "low_days": low_days,
    }
