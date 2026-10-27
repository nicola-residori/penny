// @vitest-environment jsdom
import React from 'react';import {it,expect,vi,afterEach} from 'vitest';import {render,screen,cleanup} from '@testing-library/react';import userEvent from '@testing-library/user-event';import AdminPanel from './AdminPanel';
afterEach(()=>{cleanup();vi.unstubAllGlobals();});
it('keeps reset in a dedicated authorized admin section and requires its exact phrase',async()=>{
 vi.stubGlobal('fetch',vi.fn(async(path)=>({ok:true,json:async()=>String(path).includes('/data/state')?{canManageData:true,demoLoaded:false,counts:{customers:3}}:[]})));
 render(<AdminPanel canManageData/>);expect(screen.queryByRole('button',{name:'Azzera dati CRM'})).toBeNull();await userEvent.click(screen.getByRole('button',{name:'Dati demo e reset'}));const reset=await screen.findByRole('button',{name:'Azzera dati CRM'});expect((reset as HTMLButtonElement).disabled).toBe(true);await userEvent.type(screen.getByLabelText('Scrivi RESET per confermare'),'RESET');expect((reset as HTMLButtonElement).disabled).toBe(false);expect(vi.mocked(fetch).mock.calls.some(c=>c[1]?.method==='POST')).toBe(false);
});
it('hides environment commands without DATA_MANAGE',()=>{vi.stubGlobal('fetch',vi.fn(async()=>({ok:true,json:async()=>[]})));render(<AdminPanel/>);expect(screen.queryByRole('button',{name:'Dati demo e reset'})).toBeNull();});
