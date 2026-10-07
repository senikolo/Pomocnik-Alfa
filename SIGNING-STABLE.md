# ALFA Stable Signing

Od 2026-10-07 nowe wydania Pomocnika Alfa i Ispina Lokalnie powinny używać wyłącznie stałych kluczy podpisu przechowywanych jako GitHub Actions Secrets.

## Publiczne fingerprinty certyfikatów

- Pomocnik Alfa: `36:14:42:C8:98:55:22:36:A7:D4:29:9E:ED:A5:C3:DC:27:5B:A1:F3:68:C0:11:29:2D:C0:7D:44:B2:E8:AC:E1`
- Ispina Lokalnie: `6D:75:BA:F7:F4:40:2F:F9:3C:93:50:5F:D6:D7:A5:C0:EA:B3:D1:C7:C2:AA:2A:39:EE:B5:E4:AE:16:6F:62:5F`

## Wymagane sekrety repozytorium

Pomocnik Alfa:
- `PA_STABLE_KEYSTORE_B64`
- `PA_STABLE_KEYSTORE_PASS`

Ispina Lokalnie:
- `IL_STABLE_KEYSTORE_B64`
- `IL_STABLE_KEYSTORE_PASS`

Prywatnych plików `.p12`, haseł ani wartości Base64 NIE wolno commitować do repozytorium.

## Zasada aktualizacji

Pierwsza instalacja APK podpisanego nowym stałym kluczem może wymagać usunięcia wersji podpisanej wcześniejszym kluczem. Po tej jednorazowej migracji każde następne APK dla danego `applicationId` musi być podpisane tym samym stałym kluczem.

## Kontrola bezpieczeństwa

Workflow przed podpisaniem odczytuje fingerprint certyfikatu z keystore i porównuje go z wartością zapisaną powyżej. Po podpisaniu `apksigner verify --print-certs` potwierdza poprawność podpisu. W razie niezgodności build kończy się błędem zamiast publikować APK z niewłaściwym podpisem.

## Nie regenerować kluczy

Nie używać `keytool -genkeypair` wewnątrz zwykłego workflow build/release. Klucze zostały utworzone jednorazowo i muszą być zachowane w prywatnej kopii zapasowej.
