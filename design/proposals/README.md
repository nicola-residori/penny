# Penny — proposte UI isolate

Solo STEP 1–4 della richiesta: tre dashboard dimostrative con contenuti condivisi, prima dell’approvazione. Nessun file di produzione, API, modello dati o permesso viene modificato. Questi token sono esplorazioni e non costituiscono il design system definitivo.

- `index.html?variant=a`: Modern Minimal, Manrope, tela luminosa e navigazione chiara.
- `index.html?variant=b`: Premium SaaS, Instrument Sans, navigazione petrolio e colonna operativa.
- `index.html?variant=c`: Contemporary Dark, IBM Plex Sans, rail compatto e ambra su grafite.
- `compare.html`: confronto con palette, font e screenshot desktop/mobile.
- `compare=1`: mostra il medesimo scenario con confronto anno precedente attivo.
- Gli eventuali stati `state=loading|empty|error` sono simulazioni visive.

Il prototipo usa HTML/CSS/JavaScript e la build Vite già presente nel repository; l’export XLSX riutilizza `frontend/src/xlsxExport.ts`. Nessuna dipendenza del prodotto viene aggiunta. Font auto-ospitati con licenze OFL nella cartella pubblica. I dati sono realistici ma fittizi e risiedono in `data.js`; non vengono chiamate API del CRM. Gli inserimenti sono solo in memoria.

Build nel CT111 con Node22 e il volume dei moduli frontend già disponibile: montare il repository in `/workspace`, i moduli in `/workspace/frontend/node_modules`, lavorare in `/workspace/design/proposals` ed eseguire `node /workspace/frontend/node_modules/vite/bin/vite.js build --base ./`. Servire `dist` con un server HTTP statico separato. Le catture generate dal renderer vanno in `dist/screenshots`; non fanno parte del prodotto.

La revisione funzionale e dei CSS del CRM resta in `work/ui-redesign/analysis-and-checklist.md` della chat. La scelta finale viene richiesta all’utente: non eseguire il redesign dell’applicazione prima dell’approvazione.

Anteprima LAN nel CT111: http://192.168.0.20:8099/compare.html (container separato `penny-design-proposals`). Produzione sulla porta80 invariata. Il periodo della demo è gennaio–ottobre2026 con precisione mensile; gli ambiti cliente usano una ripartizione illustrativa delle serie condivise, non un calcolo gestionale reale.

Verifica eseguita sul server: build Vite; browser Chromium a1440/768/390px; ricerca, confronto precedente su tre grafici e scarto per punto, dossier, drawer, esportazione XLSX, creazione prospect in memoria, stati simulati. Nessun test backend necessario per questi prototipi statici. Le schermate sono catture reali, non immagini generate. La verifica automatica di accessibilità riguarda la dashboard e non certifica l’intera applicazione o tutti gli stati.

Impeccable ha guidato tipografia, palette e controllo degli anti-pattern; Design Partner la gerarchia e la separazione tra lettura e azione; ui-consistency la checklist condivisa, i controlli e la revisione statica indipendente. Taste non era utilizzabile: i tool annunciati hanno restituito Unknown tool. Il detector Impeccable ha segnalato due volte Instrument Sans come font abusato: falso positivo rispetto alla lista indicata, che include Inter ma non Instrument Sans. La scelta del font resta intenzionale e provvisoria.

Esito finale: tutte le tre varianti superano i controlli funzionali indicati, senza errori JavaScript né overflow documento alle tre larghezze. Axe-core4.10.3 non rileva violazioni WCAG2A/AA sulla dashboard desktop renderizzata (non equivale a una certificazione). Revisione statica indipendente svolta; verifiche visive e correzioni finali svolte dall’autore/coordinatore. Nessun merge o aggiornamento della UI di produzione.
