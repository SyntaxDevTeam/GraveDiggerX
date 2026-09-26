# Integracje

## WorldGuard

WorldGuard jest opcjonalną zależnością. Jeśli miejsce śmierci znajduje się w regionie z jawnie ustawionymi właścicielami lub członkami, GraveDiggerX wymaga, aby zmarły gracz należał do regionu. Dzięki temu plugin nie umieszcza grobu gracza w cudzym chronionym obszarze.

Gdy WorldGuard nie jest zainstalowany, plugin działa bez kontroli regionów.

## Vault

Vault łączy koszt teleportacji z pluginem ekonomii. Cena pochodzi z `teleport.cost`. Jeśli Vault lub aktywny provider ekonomii nie jest dostępny, konsola zgłosi ostrzeżenie, a opłata nie zostanie pobrana.

Typowy zestaw to Vault oraz plugin dostarczający ekonomię, na przykład EssentialsX Economy.

## Bazy danych

Domyślny backend `json` nie wymaga dodatkowego serwera. Dostępne są również MariaDB/MySQL, PostgreSQL, SQLite i H2. Parametry połączenia opisuje strona [Konfiguracja](Konfiguracja.md#baza-danych).

Przed przełączeniem backendu wykonaj kopię danych i zatrzymaj serwer. Sama zmiana `database.type` nie powinna być traktowana jako automatyczna migracja między wszystkimi formatami.
