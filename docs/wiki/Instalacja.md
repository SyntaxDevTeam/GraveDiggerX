# Instalacja

## Wymagania

- serwer Paper lub Folia zgodny z API Minecraft 1.21,
- Java 21 lub nowsza wersja wymagana przez używany serwer,
- opcjonalnie WorldGuard 7.x,
- opcjonalnie Vault i plugin ekonomii, jeśli teleportacja ma pobierać opłatę.

## Uruchomienie

1. Pobierz najnowszy plik JAR ze strony [Releases](https://github.com/SyntaxDevTeam/GraveDiggerX/releases).
2. Umieść go w katalogu `plugins/` serwera.
3. Uruchom serwer i poczekaj na utworzenie katalogu `plugins/GraveDiggerX/`.
4. Zatrzymaj serwer i dostosuj `config.yml`, jeśli ustawienia domyślne Ci nie odpowiadają.
5. Uruchom serwer ponownie.

Po pierwszym uruchomieniu plugin tworzy konfigurację, dane grobów i pliki językowe. Prawidłowe załadowanie potwierdzisz poleceniem `/gdx help`.

## Aktualizacja

Przed wymianą pliku JAR wykonaj kopię katalogu `plugins/GraveDiggerX/`. Następnie zatrzymaj serwer, podmień plugin i uruchom serwer ponownie. Nie zaleca się aktualizowania pluginu przez mechanizmy typu PlugMan.

Szczegóły automatycznego sprawdzania aktualizacji opisuje [konfiguracja](Konfiguracja.md#aktualizacje-i-diagnostyka).
