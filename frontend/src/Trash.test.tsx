// @vitest-environment jsdom
import React from 'react';
import {it,expect,vi,afterEach} from 'vitest';
import {render,screen,cleanup,waitFor} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import TrashButton from './TrashButton';
import TrashPanel from './TrashPanel';
afterEach(()=>{cleanup();vi.restoreAllMocks();vi.unstubAllGlobals();});
const entry={id:4,entityType:'CUSTOMER',entityId:7,label:'Acme',deletedAt:'2026-10-24T10:00:00Z',expiresAt:'2026-11-23T10:00:00Z',daysRemaining:29,expired:false,blockedReason:null,canRestore:true};
it('confirms 30 days and customer contacts before deleting through the trash API',async()=>{
 const done=vi.fn();vi.spyOn(window,'confirm').mockReturnValue(true);const calls:string[]=[];
 vi.stubGlobal('fetch',vi.fn(async(u:string)=>{calls.push(u);return {ok:true,json:async()=>u.includes('csrf')?{token:'x'}:{trashed:true}};}));
 render(<TrashButton entityType="CUSTOMER" entityId={7} label="Acme" onTrashed={done}/>);await userEvent.click(screen.getByRole('button',{name:'Elimina'}));
 await waitFor(()=>expect(done).toHaveBeenCalledOnce());expect(window.confirm).toHaveBeenCalledWith(expect.stringMatching(/30 giorni/));expect(window.confirm).toHaveBeenCalledWith(expect.stringMatching(/contatti/i));expect(calls).toContain('/api/trash/CUSTOMER/7');
});
it('shows expiry and blocked reasons, restores and removes stale rows',async()=>{
 let restored=false;vi.stubGlobal('fetch',vi.fn(async(u:string,o?:RequestInit)=>{if(o?.method==='POST')restored=true;return {ok:true,json:async()=>u.includes('csrf')?{token:'x'}:o?.method==='POST'?{restored:true}:{items:restored?[]:[entry,{...entry,id:5,label:'Scaduto',expired:true,blockedReason:'Vincolo storico'}],total:restored?0:2}};}));
 render(<TrashPanel/>);await screen.findByText('Acme');expect(screen.getAllByRole('button',{name:'Ripristina dal cestino'})).toHaveLength(1);expect(screen.getByText('Vincolo storico')).toBeTruthy();expect(screen.getByText(/Non più ripristinabile/)).toBeTruthy();
 await userEvent.click(screen.getByRole('button',{name:'Ripristina dal cestino'}));await waitFor(()=>expect(screen.queryByText('Acme')).toBeNull());expect(await screen.findByText('Nessun elemento nel cestino.')).toBeTruthy();
});
it('retries failed loads and paginates',async()=>{
 let fail=true;const calls:string[]=[];vi.stubGlobal('fetch',vi.fn(async(u:string)=>{calls.push(u);return {ok:!fail,status:500,json:async()=>fail?{message:'Non disponibile'}:{items:[entry],total:21}};}));render(<TrashPanel/>);
 await screen.findByRole('alert');fail=false;await userEvent.click(screen.getByRole('button',{name:'Riprova'}));await screen.findByText('Acme');await userEvent.click(screen.getByRole('button',{name:'Successiva'}));await waitFor(()=>expect(calls.some(u=>u.includes('page=1'))).toBe(true));
});
it('cancels without a request and preserves backend conflict details on deletion',async()=>{
 const confirm=vi.spyOn(window,'confirm').mockReturnValue(false);const done=vi.fn();const fetcher=vi.fn(async(u:string)=>({ok:u.includes('csrf'),status:409,json:async()=>u.includes('csrf')?{token:'x'}:{message:'Cliente con vendite collegate'}}));vi.stubGlobal('fetch',fetcher);
 render(<TrashButton entityType="CUSTOMER" entityId={7} label="Acme" onTrashed={done}/>);await userEvent.click(screen.getByRole('button',{name:'Elimina'}));expect(fetcher).not.toHaveBeenCalled();confirm.mockReturnValue(true);await userEvent.click(screen.getByRole('button',{name:'Elimina'}));expect(await screen.findByRole('alert')).toHaveProperty('textContent','Cliente con vendite collegate');expect(done).not.toHaveBeenCalled();
});
it('preserves restore errors and excludes restore controls when permission is absent',async()=>{
 vi.stubGlobal('fetch',vi.fn(async(u:string,o?:RequestInit)=>({ok:o?.method!=='POST',status:410,json:async()=>u.includes('csrf')?{token:'x'}:o?.method==='POST'?{message:'Periodo di ripristino scaduto'}:{items:[entry,{...entry,id:5,label:'Senza permessi',canRestore:false}],total:2}})));
 render(<TrashPanel/>);await screen.findByText('Senza permessi');expect(screen.getAllByRole('button',{name:'Ripristina dal cestino'})).toHaveLength(1);await userEvent.click(screen.getByRole('button',{name:'Ripristina dal cestino'}));expect(await screen.findByRole('alert')).toHaveProperty('textContent','Periodo di ripristino scaduto');
});
it('clears the previous entity error when selection changes',async()=>{
 vi.spyOn(window,'confirm').mockReturnValue(true);vi.stubGlobal('fetch',vi.fn(async(u:string)=>({ok:u.includes('csrf'),status:409,json:async()=>u.includes('csrf')?{token:'x'}:{message:'Collegamenti esistenti'}})));
 const view=render(<TrashButton entityType="CUSTOMER" entityId={7} label="Acme" onTrashed={()=>{}}/>);await userEvent.click(screen.getByRole('button',{name:'Elimina'}));await screen.findByRole('alert');view.rerender(<TrashButton entityType="CUSTOMER" entityId={8} label="Beta" onTrashed={()=>{}}/>);expect(screen.queryByRole('alert')).toBeNull();
});
it('requires irreversible confirmation before emptying all pages and refreshes the result',async()=>{
 let emptied=false;const writes:string[]=[];const confirm=vi.spyOn(window,'confirm').mockReturnValue(false);
 vi.stubGlobal('fetch',vi.fn(async(u:string,o?:RequestInit)=>{if(o?.method==='POST'){emptied=true;writes.push(u);}return {ok:true,json:async()=>u.includes('csrf')?{token:'x'}:o?.method==='POST'?{purged:21,blocked:0,skipped:0}:{items:emptied?[]:[entry],total:emptied?0:21,canEmpty:true,purgeableTotal:emptied?0:21}};}));
 render(<TrashPanel/>);await screen.findByText('Acme');await userEvent.click(screen.getByRole('button',{name:'Svuota cestino'}));expect(writes).toHaveLength(0);confirm.mockReturnValue(true);await userEvent.click(screen.getByRole('button',{name:'Svuota cestino'}));
 await waitFor(()=>expect(writes).toContain('/api/trash/empty'));expect(window.confirm).toHaveBeenCalledWith(expect.stringMatching(/definitiv/i));expect(await screen.findByText('Nessun elemento nel cestino.')).toBeTruthy();
});
it('does not offer emptying without permission',async()=>{
 vi.stubGlobal('fetch',vi.fn(async()=>({ok:true,json:async()=>({items:[entry],total:1,canEmpty:false,purgeableTotal:0})})));render(<TrashPanel/>);await screen.findByText('Acme');expect(screen.queryByRole('button',{name:'Svuota cestino'})).toBeNull();
});
it('reports partial emptying and disables other operations while it runs',async()=>{
 vi.spyOn(window,'confirm').mockReturnValue(true);let complete:(value:unknown)=>void=()=>{};
 vi.stubGlobal('fetch',vi.fn(async(u:string,o?:RequestInit)=>u==='/api/trash/empty'?new Promise(resolve=>{complete=resolve;}):({ok:true,json:async()=>u.includes('csrf')?{token:'x'}:{items:[entry],total:1,canEmpty:true,purgeableTotal:3}})));
 render(<TrashPanel/>);await screen.findByText('Acme');await userEvent.click(screen.getByRole('button',{name:'Svuota cestino'}));
 await waitFor(()=>expect(screen.getByRole('button',{name:'Svuotamento…'})).toHaveProperty('disabled',true));expect(screen.getByRole('button',{name:'Ripristina dal cestino'})).toHaveProperty('disabled',true);
 complete({ok:true,json:async()=>({purged:1,blocked:1,skipped:1})});expect(await screen.findByText(/1 eliminati definitivamente, 1 bloccati.*1 non elaborati/)).toBeTruthy();expect(await screen.findByText('Acme')).toBeTruthy();
});
it('preserves manual emptying errors and retained rows',async()=>{
 vi.spyOn(window,'confirm').mockReturnValue(true);
 vi.stubGlobal('fetch',vi.fn(async(u:string)=>({ok:u!=='/api/trash/empty',status:403,json:async()=>u.includes('csrf')?{token:'x'}:u==='/api/trash/empty'?{message:'Permesso revocato'}:{items:[entry],total:1,canEmpty:true,purgeableTotal:1}})));
 render(<TrashPanel/>);await screen.findByText('Acme');await userEvent.click(screen.getByRole('button',{name:'Svuota cestino'}));expect(await screen.findByRole('alert')).toHaveProperty('textContent','Permesso revocato');expect(screen.getByText('Acme')).toBeTruthy();
});
