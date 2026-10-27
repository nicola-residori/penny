// @vitest-environment jsdom
import React from 'react';
import {render,screen,waitFor,fireEvent,cleanup} from '@testing-library/react';
import {afterEach,it,expect,vi} from 'vitest';
import CalendarPanel from './CalendarPanel';
vi.mock('./api',()=>({api:vi.fn()}));
import {api} from './api';
afterEach(()=>{cleanup();vi.resetAllMocks();});
it('loads the displayed month and distinguishes third party deadlines',async()=>{
 vi.mocked(api).mockResolvedValue({items:[{productId:1,customerId:1,customerName:'Alfa',productName:'Server',origin:'THIRD_PARTY',date:'2026-10-28',type:'WARRANTY'}]});
 render(<CalendarPanel initialMonth="2026-10"/>);
 expect(await screen.findByText('Server')).toBeTruthy();expect(screen.getAllByText(/Terze parti/)[0]).toBeTruthy();
 expect(vi.mocked(api).mock.calls[0][0]).toContain('from=2026-10-01');
 fireEvent.click(screen.getByRole('button',{name:'Mese successivo'}));
 await waitFor(()=>expect(vi.mocked(api).mock.calls.at(-1)?.[0]).toContain('from=2026-11-01'));
});
it('shows request errors instead of an empty calendar',async()=>{vi.mocked(api).mockRejectedValue(new Error('Accesso negato'));render(<CalendarPanel/>);expect(await screen.findByRole('alert')).toHaveProperty('textContent','Accesso negato');});
