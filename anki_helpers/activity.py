from datetime import datetime, timedelta
def analyze_activity(col) -> dict:
    """Analysiert die Aktivität der letzten 30 Tage anhand des Revlogs."""
    cutoff = (col.sched.day_cutoff - 86400 * 30) * 1000

    # Hol alle Review-Einträge der letzten 30 Tage
    rows = col.db.all("""
        SELECT id FROM revlog
        WHERE id > ? AND type IN (0, 1, 2, 3, 4)
    """, cutoff)

    # Extrahiere das Datum aus den IDs (ms seit Unix-Zeit)
    dates = [datetime.fromtimestamp(row[0] / 1000).date() for row in rows]

    # Zähle, an wie vielen Tagen es Aktivität gab
    day_counts = {}
    for date in dates:
        day_counts[date] = day_counts.get(date, 0) + 1

    active_days = len(day_counts)
    total_reviews = sum(day_counts.values())
    average = total_reviews / 30
    low_days = sum(1 for v in day_counts.values() if v < 20)

    return {
        "active_days": active_days,
        "average_per_day": round(average, 1),
        "low_days": low_days,
    }
