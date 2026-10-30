// @vitest-environment jsdom
import React from 'react';
import {afterEach,it,expect,vi} from 'vitest';
import {render,screen,cleanup,waitFor} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
vi.mock('./xlsxExport',()=>({downloadXlsx:vi.fn()}));
import {downloadXlsx} from './xlsxExport';
import ResourceTable from './ResourceTable';
afterEach(()=>{cleanup();vi.unstubAllGlobals();localStorage.clear();vi.clearAllMocks();});
it('selects and reorders visible fields and exports only those columns',async()=>{
 vi.stubGlobal('fetch',vi.fn(async()=>({ok:true,json:async()=>({items:[{id:1,name:'Alfa',quantity:2,reference:'R1'}],total:1})})));
 render(<ResourceTable title="Colonne di prova" path="/api/test" columns={[{key:'name',label:'Nome',value:r=>(r as any).name},{key:'qty',label:'Quantità',value:r=>(r as any).quantity},{key:'ref',label:'Riferimento',value:r=>(r as any).reference,defaultVisible:false}]} onOpen={()=>{}}/>);
 await screen.findByRole('button',{name:'Alfa'});expect(screen.queryByRole('columnheader',{name:'Riferimento'})).toBeNull();
 await userEvent.click(screen.getByText('Colonne',{exact:true}));await userEvent.click(screen.getByLabelText('Riferimento',{exact:true}));await userEvent.click(screen.getByRole('button',{name:'Sposta prima Riferimento'}));
 await userEvent.click(screen.getByLabelText('Quantità',{exact:true}));await userEvent.click(screen.getByRole('button',{name:'Esporta Excel'}));
 expect(downloadXlsx).toHaveBeenCalledWith('Colonne di prova',['Nome','Riferimento'],[['Alfa','R1']]);
 expect(JSON.parse(localStorage.getItem('penny.columns.v1.Colonne di prova')!).hidden).toContain('qty');
});
