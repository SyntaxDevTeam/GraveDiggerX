# Administracja

## Kopie grobów

Przy włączonym `graves.backups.enabled` plugin zapisuje kopie zawartości grobów. Użyj:

```text
/gdx admin backup list <gracz>
/gdx admin backup restore <gracz> <numer>
```

Kopie powstają przy tworzeniu grobu i są zapisywane w `plugins/GraveDiggerX/backups.json`. Lista jest sortowana od najnowszego grobu; numer `1` oznacza najnowszą kopię.

Numer pochodzi z aktualnie wyświetlonej listy. Przywrócenie tworzy aktywny grób z zapisanej kopii; przed operacją upewnij się, że gracz nie odzyskał już tych samych przedmiotów inną drogą.

Przykład odzyskania przedmiotów:

1. Wykonaj `/gdx admin backup list Gabriela_Elza` i sprawdź datę oraz świat kopii.
2. Wykonaj `/gdx admin backup restore Gabriela_Elza 1`.
3. Przywrócenie odtwarza grób (głowę gracza) w zapisanym świecie i pod zapisanymi koordynatami. Nie przekazuje przedmiotów bezpośrednio do ekwipunku; gracz może być offline podczas odtwarzania.
4. Właściciel musi dotrzeć do grobu i użyć PPM (GUI) albo Shift + PPM (szybki odbiór), mając uprawnienie `gdx.opengrave`.

Przywrócony grób otrzymuje pełny czas `graves.grave-despawn` liczony od przywrócenia (w dostarczonym configu: 120 sekund). Potem działa `graves.expiration-action`; przy `DISAPPEAR` grób znika razem z zawartością. Jeżeli dotarcie trwa zbyt długo, zwiększ ten czas przed przywróceniem i przeładuj konfigurację. `/gdx admin list Gabriela_Elza` pokazuje aktywne groby, a `backup list` pokazuje archiwalne kopie, także po usunięciu grobu.

Jeżeli wskazany świat nie jest załadowany, przywrócenie zostanie odrzucone. Załaduj właściwy świat i ponów komendę; kopia zachowuje jego nazwę. Gdy miejsce zajmuje inny aktywny grób, komenda również odmawia przywrócenia. Kopia pozostaje na liście po przywróceniu — ponowne odtwarzanie po odebraniu przedmiotów może je zduplikować.

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
