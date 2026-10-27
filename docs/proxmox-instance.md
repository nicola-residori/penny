# Installazione del 27 ottobre 2026

- Nodo Proxmox: `192.168.1.4`
- CT: **111**, hostname **penny**, Debian 13, non privilegiato, nesting/keyctl
- Risorse: 2 CPU, 3 GiB RAM, 512 MiB swap, 24 GiB su `local-lvm`
- Indirizzo DHCP: **192.168.3.46/22**
- MAC: `BC:24:11:CD:42:23` (utilizzabile per una prenotazione DHCP)
- CRM: **http://192.168.3.46/**
- Directory: `/opt/penny`
- File runtime riservato: `/opt/penny/.env`, permessi `0600`, non versionato
- Compose: `compose.production.yaml`; solo HTTP esposto, database/backend interni
- Avvio automatico CT e riavvio automatico servizi Docker
- Migrazione: Flyway **119**, **1.331 record in 23 tabelle**, confronto di tutti i valori riuscito
- Gli utenti del CRM conservano le password precedenti

Per gestire il CT dal Mac:

```sh
ssh root@192.168.1.4
pct enter 111
cd /opt/penny
docker compose -f compose.production.yaml ps
```

Il dump iniziale è conservato nella directory privata di lavoro sul Mac e in `/root/penny-migration.dump` nel CT. Il backup di controllo successivo è in `/root/penny-backups/`. Questi file contengono dati e hash delle password e non appartengono al repository Git.

Il Mac rimane un'installazione separata: modifiche effettuate lì dopo il trasferimento non sono sincronizzate con il CT. Utilizzare il nuovo indirizzo per il lavoro sul server. Non è stato modificato il CT del reverse proxy né pubblicato il CRM su Internet.
