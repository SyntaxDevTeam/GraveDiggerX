# Administracja

## Kopie grobów

Przy włączonym `graves.backups.enabled` plugin zapisuje kopie zawartości grobów. Użyj:

```text
/gdx admin backup list <gracz>
/gdx admin backup restore <gracz> <numer>
```

Numer pochodzi z aktualnie wyświetlonej listy. Przywrócenie tworzy aktywny grób z zapisanej kopii; przed operacją upewnij się, że gracz nie odzyskał już tych samych przedmiotów inną drogą.

## Usuwanie grobu

```text
/gdx admin list <gracz>
/gdx admin remove <gracz> <numer>
```

Lista numerowana jest od `1`. Po każdej zmianie wyświetl ją ponownie, ponieważ numery mogą się przesunąć.

## Czyszczenie świata

Po awarii lub ręcznym usunięciu danych mogą pozostać obiekty niepowiązane z aktywnym grobem:

```text
/gdx admin cleanupholograms
/gdx admin cleanupghosts
/gdx admin cleanupgraves
```

Limit pracy na tick ustawia `performance.cleanup.limit-per-tick`. Operacje mogą potrwać kilka ticków na dużym świecie.

## Bezpieczne utrzymanie

- Regularnie kopiuj cały katalog `plugins/GraveDiggerX/` oraz zewnętrzną bazę SQL.
- Aktualizuj plugin przy zatrzymanym serwerze.
- Po zmianie konfiguracji sprawdź konsolę pod kątem błędów połączenia i nieznanych wartości.
- Hasła do bazy przechowuj poza publicznym repozytorium i ogranicz konto SQL do bazy pluginu.
