# Riallineamento README PENNY

Fonte: docs/sources/README_PENNY.md, con successive correzioni esplicite dell'utente su contatti condivisi, sede facoltativa e conversione automatica Prospect→Cliente alla prima vendita. Autonomia autorizzata; nessun merge/commit automatico.

- [x] Catalogo: prezzo EUR esatto opzionale, fornitore distinto dal produttore, tipologia Formazione, etichette origine corrette. V110 additiva.
- [x] Installato: fornitore distinto, quantità e utenti attivati opzionali senza inventare dati storici; formazione non installabile, servizio legacy conservato. V111 additiva. UI e API coerenti, origine distinta dalla vendita.
- [x] Paginazione uniforme: conteggio singola pagina; controlli affiancati solo su più pagine. Preservare selezione, busy e filtri.
- [x] Verifiche mirate sicurezza reale: login senza CSRF403, logout, cambio password obbligatorio, disabilitazione/reset, BCrypt e limitazione tentativi. Nessun secondo bean.
- [x] Dati demo idempotenti e comando reset amministrativo con conferma esplicita: reset dati CRM, conserva utenti/ruoli/audit. V112. Non eseguire reset live; caricare demo live soltanto al termine, come richiesto.
- [x] Registro di conformità README e stato reale; M2 dettagli non concordati, funzioni esistenti preservate senza spacciarle per accordi originali. Test/build e revisione, backup sorgenti+DB, applicazione su codex/readme-alignment, verifica dati storici, poi seed demo autorizzato.

Non introdurre contratti, ordini, Gantt o regole provvigionali non definite dal README. Il reset è un'azione esplicitamente richiesta dall'utente, deve essere concreta e revisionabile e non automatica durante l'aggiornamento.
