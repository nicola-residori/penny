// @vitest-environment jsdom
import React from 'react';
import {afterEach,expect,test,vi} from 'vitest';
import {cleanup,fireEvent,render,screen} from '@testing-library/react';
import Pagination from './Pagination';
afterEach(cleanup);
test('single-page results show only their count',()=>{render(<Pagination total={1} page={0} onPageChange={vi.fn()} singular="prodotto" plural="prodotti" label="Prodotti"/>);expect(screen.getByText('1 prodotto')).toBeTruthy();expect(screen.queryByRole('button')).toBeNull();expect(screen.queryByText(/Pagina/)).toBeNull();});
test('multiple pages have a separate count and adjacent navigation with bounded disabled states',()=>{const change=vi.fn();const {rerender}=render(<Pagination total={41} page={0} onPageChange={change} singular="prodotto" plural="prodotti" label="Prodotti"/>);expect(screen.getByText('Pagina 1 di 3')).toBeTruthy();expect((screen.getByRole('button',{name:'Precedente'}) as HTMLButtonElement).disabled).toBe(true);fireEvent.click(screen.getByRole('button',{name:'Successiva'}));expect(change).toHaveBeenCalledWith(1);rerender(<Pagination total={41} page={2} disabled onPageChange={change} singular="prodotto" plural="prodotti" label="Prodotti"/>);expect(screen.getAllByRole('button').every(b=>(b as HTMLButtonElement).disabled)).toBe(true);});
