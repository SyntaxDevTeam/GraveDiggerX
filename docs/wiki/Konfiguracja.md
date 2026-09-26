# Konfiguracja

Główny plik ustawień znajduje się w `plugins/GraveDiggerX/config.yml`. Po zmianach użyj `/gdx reload` albo uruchom serwer ponownie. Zmianę typu bazy danych najlepiej wykonywać przy wyłączonym serwerze.

## Język

| Ścieżka | Domyślnie | Opis |
| --- | --- | --- |
| `language` | `EN` | Język komunikatów. Plugin zawiera pliki EN, PL, RU i UK. |

## Groby

| Ścieżka | Domyślnie | Opis |
| --- | ---: | --- |
| `graves.grave-despawn` | `120` | Czas życia grobu w sekundach. |
| `graves.max-per-player` | `2` | Maksymalna liczba aktywnych grobów gracza. |
| `graves.expiration-action` | `DISAPPEAR` | Zachowanie po wygaśnięciu: `DISAPPEAR`, `DROP_ITEMS` lub `BECOME_PUBLIC`. |
| `graves.protected-effect-cooldown` | `5000` | Odstęp w ms między efektami ochrony prywatnego grobu. |
| `graves.worlds.overworld` | `true` | Tworzenie grobów w zwykłym świecie. |
| `graves.worlds.nether` | `true` | Tworzenie grobów w Netherze. |
| `graves.worlds.end` | `true` | Tworzenie grobów w Endzie. |

## Ochrona i odbieranie

Opcje `graves.protection.explosions`, `fluids`, `mobs`, `pistons` i `hoppers` określają, przed czym chroniony jest blok grobu. Wszystkie są domyślnie włączone.

Sekcja `graves.collection` kontroluje wewnętrzną transakcję odbioru przedmiotów:

| Ścieżka | Domyślnie | Opis |
| --- | ---: | --- |
| `claim-ttl-ms` | `15000` | Czas ważności zajęcia transakcji. |
| `stuck-threshold-ms` | `30000` | Próg uznania transakcji za zablokowaną. |
| `recovery-interval-ticks` | `200` | Częstotliwość automatycznego odzyskiwania transakcji. |

Te wartości są ustawieniami technicznymi. Zmieniaj je tylko podczas diagnozowania problemów z równoczesnym odbieraniem grobu.

## Kopie zapasowe

| Ścieżka | Domyślnie | Opis |
| --- | ---: | --- |
| `graves.backups.enabled` | `true` | Zapisuje kopie zawartości grobów. |
| `graves.backups.max-per-player` | `50` | Limit kopii jednego gracza; `0` oznacza brak limitu. |
| `graves.backups.max-total` | `5000` | Globalny limit kopii; `0` oznacza brak limitu. |

## Teleportacja i duch

| Ścieżka | Domyślnie | Opis |
| --- | ---: | --- |
| `teleport.enabled` | `true` | Włącza menu i teleportację przez `/gdx tp`. |
| `teleport.cost` | `100.0` | Cena teleportacji pobierana przez Vault. |
| `spirits.enabled` | `true` | Tworzy ducha Allay nad grobem. |

## Baza danych

`database.type` obsługuje wartości `json`, `mariadb`, `mysql`, `postgresql`, `sqlite` oraz `h2`. Dla serwera SQL ustaw `database.sql.host`, `port`, `dbname`, `username` i `password`. Nie publikuj pliku zawierającego prawdziwe hasło.

## Wydajność

`performance.cleanup.limit-per-tick` określa maksymalną liczbę obiektów sprawdzanych w jednym ticku przez administracyjne komendy czyszczenia. Niższa wartość zmniejsza chwilowe obciążenie, ale wydłuża operację.

## Aktualizacje i diagnostyka

- `update.check-for-updates` — sprawdzanie nowych wersji,
- `update.auto-download` — automatyczne pobieranie,
- `update.hangar`, `update.github`, `update.modrinth` — aktywne źródła,
- `debug` — rozszerzone logowanie,
- `stats.enabled` — anonimowe statystyki użycia.
