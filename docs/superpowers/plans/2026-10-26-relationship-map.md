# Organigramma relazioni

Autonomia già autorizzata. React/CSS e API JDBC esistenti; nessuna dipendenza grafica. Branch dedicato, nessun commit/merge automatico.

Scelta: albero espandibile responsive nella nuova scheda Organigramma e vista centrata su un contatto apribile da ogni scheda contatto. Azienda → sedi → contatti; associazioni senza sede in ramo separato. Nodo contatto → clienti/sedi/ruolo. Cliccare un cliente cambia centro della mappa; sede espande i contatti. Dati solo attivi (archiviati visibili, cestinati esclusi).

Tipologie sede multiple: LEGAL Legale viola; ADMINISTRATIVE Amministrativa blu; OPERATIONAL Operativa verde; COMMERCIAL Commerciale ambra; WAREHOUSE Magazzino arancio. Nessuna classificazione presunta per sedi esistenti; lista vuota etichettata Non classificata. Ruolo testo libero esistente, visualizzato come badge con colore deterministico; Principale separato. Nessun cambiamento a cardinalità contatto-cliente (una sede facoltativa per associazione).

- [x] Backend: V114 site_types JSONB array validato, lista vuota predefinita; PUT omesso conserva. API organigramma cliente con customer/sites/contacts; dettaglio contatto include siteTypes. RBAC CUSTOMERS_READ/WRITE esistente, niente prodotti/segreti. Test migrazione, permessi, filtri cestino, doppie tipologie, input non valido e dati preservati.
- [x] UI: badge condivisi e form multiselezione tipi; tab Organigramma con mostra/nascondi contatti, rami espandibili, breadcrumb ritorno; contatto con Organigramma anche sola lettura; stati caricamento/errore/vuoto, richieste obsolete ignorate. Test navigazione azienda→contatto→azienda, ruolo specifico, senza sede, permessi e badge multipli persistiti.
- [x] Demo: assegna tipi soltanto alle sedi E2 note con patch amministrativa separata autorizzata; nessuna tipologia inventata per dati reali.
- [x] Verifica completa, revisione indipendente, Chromium desktop/mobile, backup e attivazione locale senza perdita dati; guida e report.
