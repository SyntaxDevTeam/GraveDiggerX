# Uprawnienia

| Uprawnienie | Domyślnie | Zastosowanie |
| --- | --- | --- |
| `gdx.cmd.help` | każdy | `/gdx help` |
| `gdx.cmd.list` | każdy | `/gdx list` |
| `gdx.opengrave` | każdy | Otwieranie, odbieranie i menu teleportacji do grobu. |
| `gdx.cmd.reload` | operator | `/gdx reload` |
| `gdx.cmd.admin` | operator | Wszystkie polecenia `admin` i `dev`. |
| `gdx.owner` | nikt | Zbiorczy dostęp do funkcji GraveDiggerX. |
| `gdx.*` | nikt | Wszystkie uprawnienia pluginu. |

Operatorzy serwera zawsze przechodzą kontrolę uprawnień. Wspierane są także starsze aliasy `grx.owner` i `grx.*`, jednak nowe konfiguracje powinny używać prefiksu `gdx`.

## Przykład LuckPerms

```text
/lp group default permission set gdx.cmd.help true
/lp group default permission set gdx.cmd.list true
/lp group default permission set gdx.opengrave true
/lp group admin permission set gdx.* true
```

Aby jawnie odebrać graczom jedną funkcję mimo zbiorczego uprawnienia, ustaw konkretny węzeł na `false` w systemie uprawnień.
