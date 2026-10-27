# Contatti condivisi e stato commerciale

Obiettivo: ogni contatto associato ad almeno un prospect/cliente; più associazioni consentite, sede facoltativa per ciascuna relazione. Stato commerciale PROSPECT/CUSTOMER esplicito e conversione automatica alla prima vendita.

Vincoli: conservare tutti i dati preesistenti e i collegamenti; stato dei record storici derivato dalle vendite registrate. Nuovi record PROSPECT per default. Archivio e cestino restano indipendenti dallo stato commerciale. Nessun merge/commit o purge live.

- [x] V109: migrazione non distruttiva relazioni contatti e stato, vincoli almeno una relazione e sede dello stesso cliente.
- [x] API contatti condivisi e associazioni con RBAC, ciclo cestino sicuro; conversione automatica alla prima vendita con audit.
- [x] Scheda contatti: crea, collega esistente, modifica relazioni, sede opzionale; impedisci ultima dissociazione.
- [x] Scheda cliente: filtro Prospect/Cliente e badge; conversione automatica alla prima vendita, senza pulsanti o modifica manuale.
- [x] Test backend/frontend/build, revisione integrità, backup e applicazione locale senza cancellare dati.
