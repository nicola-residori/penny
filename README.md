# Penny — Il CRM aziendale

CRM web per clienti e prospect, catalogo commerciale e prodotti installati/attivati. Java 21, Spring Boot 3.5.7, React 19, TypeScript, Vite, PostgreSQL 17 e Flyway; Docker Compose per sviluppo locale.

## Fonte dei requisiti e stato reale

Il riepilogo fornito dall'utente è conservato integralmente in [README_PENNY.md](docs/sources/README_PENNY.md). Le sue indicazioni di stato sono storiche: M1-06 è oggi implementata. Le correzioni successive esplicite dell'utente prevalgono su quel riepilogo: contatti condivisi, sede facoltativa, almeno un cliente/prospect associato e conversione automatica alla prima vendita.

La corrispondenza requisito/codice/verifica è riportata in [Conformità README](docs/conformita-readme.md). [Specifiche funzionali](docs/specifiche-funzionali.md) descrive i flussi attualmente supportati e distingue le estensioni dalle decisioni originali.

Implementato: autenticazione, utenti/RBAC, clienti e prospect, contatti condivisi, sedi, catalogo, installazioni/attivazioni, vendite e assegnazioni, scadenzario licenze/garanzie, archivio e cestino, dati demo e reset amministrativo. Non dichiarato completato: l'intero CRM operativo futuro.

## Principi funzionali

- **Catalogo**: ciò che l'azienda offre, indipendente dal cliente; hardware, software, servizi e formazione, produttore, fornitore, prezzo EUR opzionale e stato commerciale.
- **Installazioni/attivazioni**: ciò che il cliente utilizza, con matricola/licenza, quantità, utenti attivati e date operative. Prezzi del catalogo e quantità dell’installato storici non noti restano vuoti.
- **Prodotto nostro / Terze parti** indica chi realizza il prodotto, non chi lo ha venduto. Un prodotto di terzi rivenduto dall'azienda resta THIRD_PARTY. Il fornitore è distinto dal produttore.
- INTERNAL richiede sempre una voce di catalogo coerente; THIRD_PARTY permette censimento diretto, anche senza vendita. La formazione resta una voce commerciale e non viene trasformata in un'installazione.
- Ogni contatto ha almeno un cliente/prospect; può essere condiviso e avere una sede facoltativa diversa per ciascuna relazione.
- Le nuove anagrafiche sono Prospect e diventano Clienti alla prima vendita registrata, senza pulsanti manuali. Inserire il parco pregresso non crea una vendita né cambia lo stato.
- Archivio e cestino sono distinti. Il cestino consente ripristino entro 30 giorni e rimozione definitiva, soggetta ai vincoli di conservazione dello storico.

## Avvio locale

```bash
cd /Users/nicola/Develop/penny
docker compose up -d
```

Frontend: http://localhost:5173 — Backend health: http://localhost:8080/actuator/health

`docker compose down` arresta l'ambiente senza cancellare il database. Le migrazioni sono incrementali; ripristinare i sorgenti non annulla lo schema già applicato. Non eliminare i volumi per aggiornare il software.

## Provare il CRM

In **Amministrazione → Dati demo e reset**, gli amministratori autorizzati possono caricare una volta dati identificati da `[DEMO]`. Il caricamento aggiunge dati, conserva quelli precedenti ed è transazionale/idempotente.

Il dataset comprende aziende e privati, prospect e clienti, sedi, contatti condivisi, le quattro tipologie di catalogo, installazioni collegate e pregresse senza vendita, vendite/assegnazioni, elementi archiviati, un elemento nel cestino e scadenze passate/prossime/future.

**Azzera dati CRM** elimina tutti i dati aziendali, anche non demo, soltanto dopo avere scritto `RESET`. Conserva utenti, ruoli, permessi e audit; gli identificativi non vengono riutilizzati per non confondere la cronologia. Il permesso server `DATA_MANAGE` è assegnato inizialmente soltanto ad ADMIN. Il reset non viene eseguito automaticamente durante gli aggiornamenti.

## Test e controlli

```bash
bash scripts/verify-m2.sh
```

Lo script usa copie temporanee e un PostgreSQL separato. Esegue integrazione Maven con il filtro Spring Security reale per le verifiche di sicurezza, Vitest e build TypeScript/Vite. Il pool ridotto è limitato ai test, per permettere più contesti Spring nello stesso processo. La CI è predisposta anche per i branch `codex/**`; il suo esito remoto richiede pubblicazione e non è dichiarato verificato localmente.

Login senza CSRF deve restituire 403, verificato anche con HTTP e server Tomcat reali per includere il dispatch della pagina di errore; logout, cambio password obbligatorio, disabilitazione, reset delle sessioni e rate limiting hanno test dedicati. Nessun secondo bean PasswordEncoder o 2FA aggiunto.

## Estensioni preservate e ambiti futuri

Vendite, disponibilità, assegnazioni e vecchi raggruppamenti hardware sono già presenti e vengono conservati con i dati. La loro implementazione iniziale conteneva assunzioni autonome: [M2 attuale](docs/m2-sales-inventory.md). Il README fornito non concorda una sequenza o specifiche dettagliate di M2; questi moduli non vengono presentati come la totalità dei processi operativi previsti.

Preventivi, ordini, contratti, rinnovi, assistenza/interventi, gestione dell'erogazione di formazione/consulenza, Gantt e provvigioni non sono implementati da questo riallineamento. Le regole mancanti non vengono inventate o sostituite da pulsanti vuoti.

Nessun merge automatico. Le patch locali precedenti sono conservate; sincronizzazione del repository remoto e pubblicazione richiedono revisione esplicita. Le credenziali `.env` non devono essere versionate.

### Accesso dalla rete locale

Il frontend pubblica la porta 5173 sulle interfacce del Mac. Da un altro dispositivo sulla stessa rete aprire `http://IP_LOCALE_DEL_MAC:5173`; le chiamate `/api` passano attraverso il frontend. Backend e database restano associati a localhost. Per limitare nuovamente il sito al solo Mac, impostare `PENNY_FRONTEND_BIND=127.0.0.1` nel file `.env` e ricreare il frontend con `docker compose up -d --no-deps frontend`.

L’accesso da Internet richiede una configurazione separata della connettività (VPN/tunnel o pubblicazione su un server); l’IP locale funziona solo sulla rete locale.

## Installazione dedicata su Proxmox

La configurazione di produzione usa `compose.production.yaml`, frontend statico Nginx e backend Java compilato. Consultare [Installazione e gestione](docs/proxmox-deployment.md) per avvio, aggiornamento e backup. Non usa il server di sviluppo Vite.
