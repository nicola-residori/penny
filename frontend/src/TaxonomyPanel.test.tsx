// @vitest-environment jsdom
import React from 'react';
import {it,expect,vi,afterEach} from 'vitest';
import {render,screen,cleanup,waitFor} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import TaxonomyPanel from './TaxonomyPanel';
const category={id:1,code:'SW',name:'Software',operationalKind:'SOFTWARE',legacyCategory:'SOFTWARE',active:true,isSystem:true};
afterEach(()=>{cleanup();vi.unstubAllGlobals();});
it('marks a type obsolete using an update and preserves its record',async()=>{
 const writes:unknown[]=[];vi.stubGlobal('fetch',vi.fn(async(url:string,o?:RequestInit)=>{if(o?.method==='PUT')writes.push(JSON.parse(String(o.body)));return {ok:true,json:async()=>url.includes('/csrf')?{token:'t'}:url.includes('/product-categories')?{items:[category]}:{items:[{id:3,categoryId:1,name:'Autodesk',obsolete:false}]}};}));
 render(<TaxonomyPanel canWrite/>);const user=userEvent.setup();await user.click(await screen.findByRole('button',{name:'Modifica tipologia Autodesk'}));await user.click(screen.getByLabelText('Tipologia obsoleta'));await user.click(screen.getByRole('button',{name:'Salva classificazione'}));await waitFor(()=>expect(writes).toContainEqual({categoryId:1,name:'Autodesk',obsolete:true}));
});
it('shows obsolete labels but hides category and type mutations without write permission',async()=>{
 vi.stubGlobal('fetch',vi.fn(async(url:string)=>({ok:true,json:async()=>url.includes('/product-categories')?{items:[category]}:{items:[{id:3,categoryId:1,name:'Autodesk',obsolete:true}]}})));
 render(<TaxonomyPanel canWrite={false}/>);expect(await screen.findByText('Obsoleto')).toBeTruthy();expect(screen.queryByRole('button')).toBeNull();
});
