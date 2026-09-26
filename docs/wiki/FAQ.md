# Pytania i problemy

## Grób nie powstał

Sprawdź, czy dany wymiar jest włączony w `graves.worlds`, czy gracz nie przekroczył `graves.max-per-player` oraz czy WorldGuard pozwala mu na użycie regionu. Zajrzyj też do konsoli, szczególnie gdy używasz SQL.

## `/gdx tp` nie działa

Gracz potrzebuje `gdx.opengrave`, musi mieć aktywny grób, a `teleport.enabled` musi mieć wartość `true`. Przy płatnej teleportacji sprawdź także Vault, provider ekonomii i saldo gracza.

## Gracz nie może otworzyć grobu

Wymagane jest `gdx.opengrave`. Prywatny grób może odebrać jego właściciel; publiczny dostęp pojawia się dopiero po wygaśnięciu, jeśli ustawiono `BECOME_PUBLIC`.

## Został hologram, duch albo blok grobu

Użyj odpowiedniej komendy czyszczenia opisanej na stronie [Administracja](Administracja.md#czyszczenie-świata).

## Jak przywrócić utracone przedmioty?

Jeżeli backupy były włączone, administrator może wyświetlić je i przywrócić komendami `admin backup`. Przywracaj kopię ostrożnie, aby nie zduplikować przedmiotów.

## Gdzie zgłosić błąd?

Przygotuj wersję serwera, wersję GraveDiggerX, używany backend danych, fragment logu i kroki odtwarzające problem. Następnie utwórz zgłoszenie w [GitHub Issues](https://github.com/SyntaxDevTeam/GraveDiggerX/issues) albo skontaktuj się przez [Discord SyntaxDevTeam](https://discord.gg/Zk6mxv7eMh).
