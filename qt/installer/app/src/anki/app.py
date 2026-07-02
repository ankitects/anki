# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html


# MCAT Speedrun fork: names of the two profiles the packaged installer ships
# with on a brand-new machine.
#   * CLEAN_PROFILE  — an empty clean-slate profile; opened by default.
#   * SEEDED_PROFILE — a pre-seeded profile whose collection already carries
#                      FSRS memory data across the MileDown topics, so the Topic
#                      Mastery dashboard demonstrates per-topic memory scores out
#                      of the box. Reachable via File -> Switch Profile.
CLEAN_PROFILE = "New"
SEEDED_PROFILE = "In Progress"


def main():
    _seed_demo_profiles_on_first_run()

    import aqt

    aqt.run()


def _seed_demo_profiles_on_first_run() -> None:
    """On the FIRST launch of the packaged app (no Anki base folder yet), create
    two profiles instead of the single stock default: a clean-slate profile that
    opens by default, plus a pre-seeded "In Progress" profile for the Topic
    Mastery demo. A no-op on every later launch (and for existing installs), and
    it never blocks startup — any failure falls back to stock first-run."""
    import os

    # The seed rides along in the bundle next to this launcher (sources=['src/anki']).
    seed = os.path.join(
        os.path.dirname(os.path.abspath(__file__)), "seed", "in_progress.anki2"
    )
    if not os.path.exists(seed):
        return  # seed not bundled -> let Anki do its normal first-run

    try:
        from aqt.profiles import ProfileManager

        base = str(ProfileManager.get_created_base_folder(None))
        # Only seed a fresh base; never touch an existing user's data.
        if os.path.exists(os.path.join(base, "prefs21.db")):
            return

        pm = ProfileManager(base)
        pm.setupMeta()
        pm.create(CLEAN_PROFILE)
        pm.create(SEEDED_PROFILE)

        # Drop the pre-seeded collection into the seeded profile's folder; Anki
        # opens <base>/<profile>/collection.anki2 when that profile is loaded.
        import shutil

        seeded_dir = os.path.join(base, SEEDED_PROFILE)
        os.makedirs(seeded_dir, exist_ok=True)
        shutil.copyfile(seed, os.path.join(seeded_dir, "collection.anki2"))

        # Auto-open the clean profile (no profile picker): with >1 profile Anki
        # opens `last_loaded_profile_name`, and firstRun=False so it doesn't
        # force-load profiles()[0] either. (profiles() is ordered alphabetically
        # by the name primary key, not by creation order, so we rely on these two
        # meta keys rather than insertion order to pick the default profile.)
        pm.meta["firstRun"] = False
        pm.set_last_loaded_profile_name(CLEAN_PROFILE)
        pm.db.execute(
            "update profiles set data = ? where name = ?",
            pm._pickle(pm.meta),
            "_global",
        )
        pm.db.commit()
        pm.db.close()
    except Exception:
        # A fresh base with a partial seed still opens a clean profile (firstRun
        # force-loads profiles()[0]); swallow and let Anki continue.
        pass
