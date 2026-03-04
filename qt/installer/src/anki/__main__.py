from anki.app import main

if __name__ == "__main__":
    import sys
    from pathlib import Path

    try:
        sys.path.remove(str(Path(__file__).parent.parent))
    except Exception:
        pass
    sys.modules.pop("anki")
    sys.modules.pop("anki.app")

    main()
