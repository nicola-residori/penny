// @vitest-environment jsdom
import React from 'react';
import {afterEach,it,expect,vi} from 'vitest';
import {cleanup,render,screen,within} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
vi.mock('./SalesPanel',()=>({default:()=> <p>Sales content</p>}));
vi.mock('./InventoryPanel',()=>({default:()=> <p>Inventory content</p>}));
vi.mock('./WorkstationsPanel',()=>({default:()=> <p>Workstations content</p>}));
import {Shell} from './main';
afterEach(()=>{cleanup();vi.unstubAllGlobals();});
const me={name:'Ada Rossi',email:'ada@example.test',mustChangePassword:false};
it('keeps unauthorized modules out of navigation and supports keyboard navigation',async()=>{
 vi.stubGlobal('fetch',vi.fn(async()=>({ok:true,json:async()=>({})})));vi.stubGlobal('requestAnimationFrame',(callback:()=>void)=>callback());
 render(<Shell me={{...me,canReadSales:true}} onLogout={()=>{}} onRefresh={()=>{}}/>);
 const nav=within(screen.getByRole('navigation',{name:'Moduli'}));expect(nav.queryByRole('button',{name:'Disponibilità'})).toBeNull();expect(nav.queryByRole('button',{name:'Clienti'})).toBeNull();
 const button=nav.getByRole('button',{name:'Vendite'});button.focus();await userEvent.keyboard('{Enter}');expect(screen.getByText('Sales content')).toBeTruthy();expect(button.getAttribute('aria-current')).toBe('page');expect(document.activeElement?.id).toBe('penny-main');
});
it('closes mobile navigation with Escape and restores the toggle focus',async()=>{
 vi.stubGlobal('fetch',vi.fn(async()=>({ok:true,json:async()=>({})})));
 render(<Shell me={me} onLogout={()=>{}} onRefresh={()=>{}}/>);await userEvent.click(screen.getByRole('button',{name:'Apri navigazione'}));expect(screen.getByRole('button',{name:'Chiudi navigazione',expanded:true})).toBeTruthy();await userEvent.keyboard('{Escape}');expect(screen.getByRole('button',{name:'Apri navigazione'}).getAttribute('aria-expanded')).toBe('false');expect(document.activeElement?.id).toBe('penny-menu-toggle');
});
it('opens the trash only with its profile capability',async()=>{
 vi.stubGlobal('fetch',vi.fn(async(url:string)=>({ok:true,json:async()=>url.startsWith('/api/trash')?{items:[],total:0}:{}})));vi.stubGlobal('requestAnimationFrame',(callback:()=>void)=>callback());
 const view=render(<Shell me={me} onLogout={()=>{}} onRefresh={()=>{}}/>);expect(within(screen.getByRole('navigation')).queryByRole('button',{name:'Cestino'})).toBeNull();view.rerender(<Shell me={{...me,canUseTrash:true}} onLogout={()=>{}} onRefresh={()=>{}}/>);await userEvent.click(screen.getByRole('button',{name:'Cestino'}));expect(await screen.findByText('Nessun elemento nel cestino.')).toBeTruthy();
});
it('does not expose legacy workstation management as Hardware',async()=>{
 vi.stubGlobal('fetch',vi.fn(async()=>({ok:true,json:async()=>({})})));render(<Shell me={{...me,canReadWorkstations:true}} onLogout={()=>{}} onRefresh={()=>{}}/>);expect(within(screen.getByRole('navigation')).queryByRole('button',{name:'Hardware'})).toBeNull();
});
