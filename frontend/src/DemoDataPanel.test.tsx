// @vitest-environment jsdom
import React from 'react';
import {afterEach,it,expect,vi} from 'vitest';
import {render,screen,cleanup,waitFor} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import DemoDataPanel from './DemoDataPanel';
afterEach(()=>{cleanup();vi.unstubAllGlobals();});
it('requires typed RESET and shows counts before resetting',async()=>{
 const fetcher=vi.fn(async(input:RequestInfo|URL)=>({ok:true,json:async()=>String(input).endsWith('/csrf')?{token:'csrf'}:{canManageData:true,demoLoaded:false,counts:{customers:12,contacts:3}}}));vi.stubGlobal('fetch',fetcher);
 render(<DemoDataPanel canManageData/>);await screen.findByText(/Clienti e prospect: 12/);
 const button=screen.getByRole('button',{name:'Azzera dati CRM'});expect((button as HTMLButtonElement).disabled).toBe(true);
 await userEvent.type(screen.getByLabelText('Scrivi RESET per confermare'),'RESET');await userEvent.click(button);
 await waitFor(()=>expect(fetcher.mock.calls.some(([url])=>url==='/api/admin/data/reset')).toBe(true));
 expect(screen.getByText(/Conserva utenti, ruoli e permessi/)).toBeTruthy();
});
it('hides data tools without permission',()=>{render(<DemoDataPanel canManageData={false}/>);expect(screen.queryByRole('button')).toBeNull();});
it('upgrades existing demo data without a reset and then prevents duplicate loads',async()=>{
 const fetcher=vi.fn(async(path:RequestInfo|URL,init?:RequestInit)=>({ok:true,json:async()=>String(path).endsWith('/csrf')?{token:'csrf'}:{canManageData:true,demoLoaded:true,scenarioVersion:init?.method==='POST'?2:1,demoUpgradeAvailable:init?.method!=='POST',counts:{customers:15}}}));vi.stubGlobal('fetch',fetcher);render(<DemoDataPanel canManageData/>);
 await userEvent.click(await screen.findByRole('button',{name:'Amplia dati demo'}));await waitFor(()=>expect(fetcher.mock.calls.some(([p,i])=>p==='/api/admin/data/demo'&&i?.method==='POST')).toBe(true));expect(await screen.findByRole('button',{name:'Demo aggiornata'})).toHaveProperty('disabled',true);expect(fetcher.mock.calls.some(([p])=>String(p).endsWith('/reset'))).toBe(false);
});
