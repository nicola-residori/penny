// @vitest-environment jsdom
import React from 'react';
import {it,expect,afterEach} from 'vitest';
import {render,screen,cleanup} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import AnalyticsChart from './AnalyticsChart';
import type {AnalyticsPoint} from './CommercialAnalytics';
afterEach(cleanup);
const point=(period:string,current:number,previous:number,gap:number,percent:string|null):AnalyticsPoint=>({period,salesAmount:String(current),soldQuantity:current,customersToDate:current,newCustomers:0,saleCount:0,comparison:{from:period.replace('2026','2025'),to:period.replace('2026','2025'),previous:{salesAmount:String(previous),soldQuantity:previous,customersToDate:previous,newCustomers:0,saleCount:0},gap:{salesAmount:String(gap),soldQuantity:gap,customersToDate:gap,newCustomers:0,saleCount:0},percent:{salesAmount:percent,soldQuantity:percent,customersToDate:percent}}});
it('scales the overlay with current values and exposes each point’s signed gap and zero base',async()=>{
 const points=[point('2026-01-01',10,100,-90,'-90'),point('2026-01-02',20,10,10,'100'),point('2026-01-03',5,0,5,null)];
 const {container}=render(<AnalyticsChart title="Risorse" description="Quantità" field="soldQuantity" span="1d" color="blue" points={points} comparison/>);
 const path=screen.getByTestId('previous-year-line');expect(path.getAttribute('d')).toContain(',20');expect(container.querySelector('.analytics-bar')?.getAttribute('height')).toBe('16.5');
 const buttons=screen.getAllByRole('button');expect(buttons[0].getAttribute('aria-label')).toContain('Scarto -90 · -90%');expect(buttons[1].getAttribute('aria-label')).toContain('Scarto +10 · +100%');expect(buttons[2].getAttribute('aria-label')).toContain('Percentuale non calcolabile');
 await userEvent.click(buttons[0]);expect(container.querySelector('.analytics-chart-readout')?.textContent).toContain('2025-01-01');expect(container.querySelector('.analytics-chart-readout')?.textContent).toContain('Scarto -90');
});
it('leaves a gap for a day without a previous-year equivalent and hides the overlay when disabled',()=>{
 const points=[{...point('2026-02-28',10,20,-10,'-50'),comparison:null}];const {rerender}=render(<AnalyticsChart title="Risorse" description="Quantità" field="soldQuantity" span="1d" color="blue" points={points} comparison/>);expect(screen.getByRole('button').getAttribute('aria-label')).toContain('Nessun periodo equivalente');expect(screen.getByTestId('previous-year-line').getAttribute('d')).toBe('');
 rerender(<AnalyticsChart title="Risorse" description="Quantità" field="soldQuantity" span="1d" color="blue" points={points}/>);expect(screen.queryByTestId('previous-year-line')).toBeNull();expect(screen.getByRole('button').getAttribute('aria-label')).not.toContain('precedente');
});
