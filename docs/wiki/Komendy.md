# Komendy

Główne polecenie to `/gravediggerx`, a jego krótki alias to `/gdx`.

## Gracze

| Komenda | Opis | Uprawnienie |
| --- | --- | --- |
| `/gdx help` | Wyświetla pomoc. | `gdx.cmd.help` |
| `/gdx list` | Pokazuje aktywne groby gracza i ich współrzędne. | `gdx.cmd.list` |
| `/gdx tp` | Otwiera menu grobów i teleportacji. | `gdx.opengrave` |
| `/gdx teleport` | Pełny alias komendy `/gdx tp`. | `gdx.opengrave` |

## Administracja

| Komenda | Opis |
| --- | --- |
| `/gdx help admin` | Wyświetla pomoc administracyjną. |
| `/gdx reload` | Przeładowuje konfigurację i wiadomości. |
| `/gdx admin list <gracz>` | Wyświetla groby wybranego gracza. |
| `/gdx admin remove <gracz> <numer>` | Usuwa grób wskazany numerem z listy. |
| `/gdx admin backup list <gracz>` | Wyświetla zapisane kopie grobów. |
| `/gdx admin backup restore <gracz> <numer>` | Przywraca wybraną kopię jako aktywny grób. |
| `/gdx admin cleanupholograms` | Usuwa osierocone hologramy grobów. |
| `/gdx admin cleanupghosts` | Usuwa osierocone duchy Allay. |
| `/gdx admin cleanupgraves` | Usuwa osierocone bloki grobów. |

Komendy administracyjne wymagają `gdx.cmd.admin`, z wyjątkiem `/gdx reload`, które wymaga `gdx.cmd.reload`.

## Diagnostyka deweloperska

Polecenia `/gdx dev stats` oraz `/gdx dev tx ...` są przeznaczone do diagnozowania działania pluginu. Mogą zmieniać stan transakcji odbioru grobów, dlatego powinny być używane wyłącznie przez administratora rozumiejącego ich działanie.
