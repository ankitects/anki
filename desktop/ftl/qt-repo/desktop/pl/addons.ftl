addons-possibly-involved = Możliwy udział dodatków: { $addons }
addons-failed-to-load =
    Nie udało się załadować zainstalowanego dodatku. Jeśli problem będzie się utrzymywał, przejdź do menu Narzędzia>Dodatki i wyłącz lub usuń dodatek.
    
    Przy ładowaniu '{ $name }':
    { $traceback }
addons-failed-to-load2 =
    Następujących dodatków nie udało się załadować:
    { $addons }
    
    Możliwe, że trzeba je zaktualizować, aby działały z tą wersją Anki. Kliknij przycisk { addons-check-for-updates } 
    aby sprawdzić, czy są dostępne uaktualnienia tych dodatków.
    
    Możesz uzyć przycisku { about-copy-debug-info }, aby uzyskać informacje, które możesz wkleić w zgłoszeniu do 
    autora dodatku
    
    Jeśli dodatek, nie ma żadnego uaktualnienia, możesz wyłączyć lub usunąć dany dodatek, aby zaprzestać 
    pojawianiu się tej wiadomości.
addons-startup-failed = Błąd przy starcie dodatku
# Shown in the add-on configuration screen (Tools>Add-ons>Config), in the title bar
addons-config-window-title = Konfiguruj "{ $name }"
addons-config-validation-error = Wystąpił problem z podaną konfiguracją: { $problem }, w ścieżce { $path }, przeciw schematowi { $schema }.
addons-window-title = Dodatki
addons-addon-has-no-configuration = Dodatek nie ma konfiguracji
addons-addon-installation-error = Błąd przy instalacji dodatku
addons-browse-addons = Przeglądaj dodatki
addons-changes-will-take-effect-when-anki = Zmiany będą miały skutek po ponownym uruchomieniu Anki
addons-check-for-updates = Sprawdź dostępność aktualizacji
addons-checking = Sprawdzanie...
addons-code = Kod:
addons-config = Konfiguracja
addons-configuration = Konfiguracja
addons-corrupt-addon-file = Zepsuty plik dodatku.
addons-disabled = (wyłączony)
addons-disabled2 = (wyłączony)
addons-download-complete-please-restart-anki-to = Pobieranie zakończone. Uruchom Anki ponownie, aby zastosować zmiany.
addons-downloaded-fnames = Pobrano { $fname }
addons-downloading-adbd-kb02fkb = Pobieranie { $part }/{ $total } ({ $kilobytes }KB)...
addons-error-downloading-ids-errors = Błąd przy pobieraniu <i>{ $id }</i>: { $error }
addons-error-installing-bases-errors = Błąd przy instalacji <i>{ $base }</i>: { $error }
addons-get-addons = Pobierz dodatki...
addons-important-as-addons-are-programs-downloaded = <b>Ważne</b>: Dodatki są pobierane z Internetu, więc mogą być złośliwymi programami.<b>Instaluj tylko te dodatki, którym ufasz.</b><br><br>Na pewno chcesz kontynuować instalację następującego dodatku/dodatków?<br><br>%(names)s
addons-install-addon = Zainstaluj dodatek
addons-install-addons = Zainstaluj dodatek (-tki)
addons-install-anki-addon = Zainstaluj dodatek
addons-install-from-file = Zainstaluj z pliku...
addons-installation-complete = Instalacja zakończona
addons-installed-names = Zainstalowano { $name }
addons-installed-successfully = Zainstalowano pomyślnie.
addons-invalid-addon-manifest = Nieprawidłowy manifest dodatku.
addons-invalid-code = Nieprawidłowy kod.
addons-invalid-code-or-addon-not-available = Kod jest nieprawidłowy lub dodatek nie jest dostępny na tę wersję Anki.
addons-invalid-configuration = Nieprawidłowa konfiguracja:
addons-invalid-configuration-top-level-object-must = Nieprawidłowa konfiguracja: obiekt na najwyższym poziomie musi być mapą
addons-no-updates-available = Brak dostępnych aktualizacji.
addons-one-or-more-errors-occurred = Wystąpił jeden lub więcej błędów:
addons-packaged-anki-addon = Spakowany dodatek Anki
addons-please-check-your-internet-connection = Sprawdź swoje połączenie internetowe.
addons-please-report-this-to-the-respective = Zgłoś problem autorom odpowiednich dodatków.
addons-please-restart-anki-to-complete-the = <b>Uruchom ponownie Anki, aby zakończyć instalację.</b>
addons-please-select-a-single-addon-first = Najpierw wybierz pojedynczy dodatek
addons-requires = (wymaga { $val })
addons-restored-defaults = Przywrócono ustawienia domyślne
addons-the-following-addons-are-incompatible-with = Następujące dodatki są niekompatybilne z { $name } i zostały wyłączone: { $found }
addons-the-following-addons-have-updates-available = Są dostępne aktualizacje dla następujących dodatków. Zainstalować je teraz?
addons-the-following-conflicting-addons-were-disabled = Następujące dodatki zostały wyłączone:
addons-this-addon-is-not-compatible-with = Ten dodatek nie jest kompatybilny z Twoją wersją Anki.
addons-to-browse-addons-please-click-the = Aby przeglądać dodatki, kliknij przycisk Przeglądaj.<br><br>Gdy znajdziesz dodatek, wklej jego kod poniżej. Możesz wkleić wiele kodów oddzielonych spacją.
addons-toggle-enabled = Włącz/wyłącz
addons-unable-to-update-or-delete-addon = Nie udało się zaktualizować lub usunąć dodatku. Zrestartuj Anki trzymając wciśnięty klawisz shift, by tymczasowo wyłączyć dodatki, a następnie spróbuj ponownie.  Informacja diagnostyczna: { $val }
addons-unknown-error = Nieznany błąd: { $val }
addons-view-addon-page = Odwiedź stronę dodatku
addons-view-files = Pokaż pliki
addons-delete-the-numd-selected-addon =
    { $count ->
        [one] Usunąć { $count } wybrany dodatek?
        [few] Usunąć { $count } wybrane dodatki?
        [many] Usunąć { $count } wybranych dodatków?
       *[other] Nie usuwać żadnego wybranego dodatku?
    }
addons-choose-update-window-title = Aktualizacja dodatków
addons-choose-update-update-all = Aktualizuj wszystkie
