# Penny su Proxmox

## Architettura

CT Debian dedicato non privilegiato, con nesting/keyctl per Docker. Nginx serve la build statica React e inoltra `/api/` al backend Java 21; PostgreSQL 17 conserva dati e immagini in un volume persistente. Solo la porta HTTP del frontend viene pubblicata. Docker e il CT riavviano automaticamente i servizi.

Le immagini si costruiscono dal codice versionato, senza server Vite e senza dipendenze di sviluppo montate dal Mac. Le credenziali appartengono al file `.env` sul server (permessi `0600`) e non al repository.

## Avvio

Nel CT, directory `/opt/penny`:

```sh
cp .env.production.example .env
chmod 600 .env
# Impostare password database casuale. Per un database vuoto servono anche le credenziali bootstrap.
docker compose -f compose.production.yaml build
docker compose -f compose.production.yaml up -d
docker compose -f compose.production.yaml ps
```

Per l'importazione iniziale di un database esistente, avviare prima il solo `db`, ripristinare il dump e avviare gli altri servizi soltanto dopo la verifica. L'importazione preserva gli utenti/password del CRM: nessun nuovo account amministratore è necessario.

## Gestione

```sh
docker compose -f compose.production.yaml logs --tail=100 backend
docker compose -f compose.production.yaml restart backend
```

Per aggiornare: salvare un backup, trasferire la nuova revisione, eseguire `build` e `up -d`. Flyway applica le nuove migrazioni all'avvio. Non eseguire `down -v`: eliminerebbe il volume database.

## Backup

Eseguire `deploy/backup-database.sh /percorso/backup` nel CT. Il dump include dati, immagini, utenti CRM e schema. Conservare anche `.env` separatamente e con accesso riservato. Prima di ripristinare, fermare il backend ed effettuare un backup dello stato corrente; usare un database nuovo o un ripristino pianificato, senza sovrascrivere dati in uso.

## Accesso

L'indirizzo DHCP del CT si legge con `pct exec CTID -- ip -4 -br addr` sul nodo Proxmox. Per un indirizzo stabile configurare una prenotazione DHCP per il MAC assegnato al CT. HTTP è destinato alla rete locale; l'eventuale reverse proxy HTTPS o accesso VPN si configura separatamente.
