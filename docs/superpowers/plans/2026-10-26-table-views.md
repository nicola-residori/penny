# Tabelle, filtri ed esportazioni

Spec docs/sources/2026-10-26-table-notes.txt; ultime istruzioni prevalgono sulle parti ripetute: postazione solo testo, catalogo solo Canale di vendita. Autonomia e branch dedicato, niente merge/commit.

- [x] Componente tabellare comune per clienti, contatti, catalogo, software/hardware, corsi/contratti/sviluppi/attività e vendite: carica tutte le pagine autorizzate per filtri completi, filtri contenuto per colonna, ordinamento, selezione dettaglio, export vero XLSX dei risultati filtrati. Nessun export parziale durante caricamento, celle stringa non formule, numeri entro precisioneExcel.
- [x] Clienti: tabella generale nome/PIVA/indirizzi e province delle sedi operative/stati; vista dettaglio con rapida a sinistra e ritorno all'elenco; box aziendali/commerciali/amministrativi. V119 adds siteprovince nullable con editor e nessuna deduzione dei dati storici.
- [x] Prodotti e schede: colonne richieste persoftwarehardwareformazione; dettaglio al clic, permessi e bulk esistente invariati. Costo collaboratore indipendente da canale nei contratti. Contratto senza nome manuale obbligatorio: tipologia prodotto obbligatoria sui nuovi, nome automatico dalla tipologia; tipi contratto esistenti restano distinti. Canale esplicito nullable, non dedotto dal costo.
- [x] Test su filtri oltre prima pagina, XLSX, contratti, conservazione migrazione; suite e build, browserdesktopmobile; backupattivazione confrontocolonneprecedenti.

Verifica conclusa: 129 test backend, suite frontend di 106 test e controlli mirati sulle ultime rifiniture, build TypeScript/Vite, 22 viste browser su due viewport e due export XLSX filtrati. Migrazione locale V119; 1.331 record storici in 23 tabelle preservati. Backup sorgenti/database e patch verificata; nessun commit, push o merge.
