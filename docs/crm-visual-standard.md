# Scheda cliente come standard grafico

La scheda cliente rimane il riferimento: carattere di sistema/Inter, blu per gerarchie e azioni, bordi azzurri, superfici chiare e indicatori pastello. Il tema comune è in `frontend/src/dossier-theme.css`; intestazioni, sezioni, coppie etichetta/valore e indicatori riutilizzabili sono in `Dossier.tsx`.

- La modale cliente contiene soltanto il cliente selezionato. La tabella rimane nella pagina sottostante; eliminato il secondo caricamento dell'elenco.
- Catalogo, installazioni e vendite usano intestazioni, badge e gruppi di informazioni comuni. Gli indicatori delle installazioni dipendono dalla categoria; i prodotti di terzi mantengono il badge distinto.
- Contatti, formazione, contratti, sviluppi e attività aprono una scheda di consultazione. Le azioni di modifica aprono esplicitamente il modulo, conservando i permessi esistenti.
- Sedi e moduli di modifica condividono stile, intestazioni, spaziature e controlli; assegnazioni e dettaglio amministrativo sono in overlay.
- Tabelle, dashboard, filtri, pulsanti e selettori condividono il tema. La scheda si dispone su una colonna su schermi piccoli. Focus, Escape, ripristino del focus e movimento ridotto restano supportati.

Verifica: suite frontend, compilazione di produzione, controllo visivo cliente/catalogo/hardware con dati simulati e controllo della scheda a 390 px senza debordamento orizzontale.

## Aggiornamenti del solo frontend

`compose.production.yaml` accetta `PENNY_FRONTEND_RELEASE`, con ripiego su `PENNY_RELEASE`. Questo permette di aggiornare la sola interfaccia senza ricostruire o cambiare la versione backend:

```sh
docker compose -f compose.production.yaml up -d --no-deps frontend
```

Il valore frontend precedente e la configurazione runtime devono essere salvati prima del rilascio per consentire il ripristino. Nessuna migrazione o modifica dei dati è necessaria per questo aggiornamento grafico.
