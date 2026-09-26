# Pierwsze kroki

## Po śmierci

Jeśli groby są włączone w danym wymiarze, plugin zapisze ekwipunek, pancerz, drugą rękę i doświadczenie, a następnie utworzy grób w bezpiecznym miejscu w pobliżu śmierci. Nad grobem pojawi się hologram z właścicielem i pozostałym czasem. Administrator może również włączyć ducha Allay.

## Odnajdź grób

- `/gdx list` pokazuje aktywne groby i ich współrzędne.
- `/gdx tp` otwiera listę grobów z czasem wygaśnięcia i kosztem teleportacji.

Teleportacja może być wyłączona albo płatna. Gdy serwer nie korzysta z Vault, plugin nie pobiera opłaty ekonomicznej.

## Odbierz zawartość

Podejdź do swojego grobu i kliknij go, aby otworzyć menu. Możesz także użyć szybkiego odbioru przez skradanie i kliknięcie. Wymagane jest uprawnienie `gdx.opengrave`.

Obcy gracz nie może odebrać prywatnego grobu. Wyjątkiem jest grób, który po wygaśnięciu stał się publiczny wskutek ustawienia `BECOME_PUBLIC`.

## Gdy skończy się czas

Zachowanie zależy od administracji:

- `DISAPPEAR` — grób i przedmioty znikają,
- `DROP_ITEMS` — zawartość wypada na ziemię,
- `BECOME_PUBLIC` — grób pozostaje i może zostać otwarty przez innych graczy.
