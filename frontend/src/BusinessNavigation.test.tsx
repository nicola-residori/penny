// @vitest-environment jsdom
import React from 'react';
import {it,expect,vi,afterEach} from 'vitest';
import {render,screen,within,cleanup} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
vi.mock('./ProductsPanel',()=>({default:()=> <p>Parco software</p>}));
vi.mock('./CustomerRecordsPanel',()=>({default:({kind}:{kind:string})=><p>Records {kind}</p>}));
import {Shell} from './main';
import CustomerWorkspace from './CustomerWorkspace';
afterEach(()=>{cleanup();vi.unstubAllGlobals();});
it('exposes general business lists by permission and removes availability navigation',async()=>{
 vi.stubGlobal('fetch',vi.fn(async()=>({ok:true,json:async()=>({})})));vi.stubGlobal('requestAnimationFrame',(f:()=>void)=>f());
 render(<Shell me={{name:'Ada',email:'a@example.invalid',mustChangePassword:false,canReadCustomers:true,canReadProducts:true,canReadInventory:true,canReadTraining:true,canReadActivities:true}} onLogout={()=>{}} onRefresh={()=>{}}/>);
 const nav=within(screen.getByRole('navigation',{name:'Moduli'}));for(const name of ['Clienti','Contatti','Software','Hardware','Formazione','Sviluppi','Attività tecniche','Attività commerciali'])expect(nav.getByRole('button',{name})).toBeTruthy();expect(nav.queryByRole('button',{name:'Disponibilità'})).toBeNull();expect(nav.queryByRole('button',{name:'Contratti'})).toBeNull();await userEvent.click(nav.getByRole('button',{name:'Software'}));expect(screen.getByText('Parco software')).toBeTruthy();
});
it('opens customer-scoped contracts without granting other modules',async()=>{
 vi.stubGlobal('fetch',vi.fn(async()=>({ok:true,json:async()=>({sites:[],products:[],deadlines:[],productsTotal:0})})));
 render(<CustomerWorkspace customerId={1} canWrite={false} canReadContracts canWriteContracts anagrafica={null} contacts={null}/>);await userEvent.click(screen.getByRole('tab',{name:'Contratti'}));expect(screen.getByText('Records CONTRACT')).toBeTruthy();expect(screen.queryByRole('tab',{name:'Formazione'})).toBeNull();
});
