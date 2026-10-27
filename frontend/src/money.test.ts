import {test,expect} from 'vitest';
import {formatEuro} from './money';
test('preserva i centesimi nei totali grandi restituiti come stringa',()=>{
 expect(formatEuro('999998999990000.01')).toContain(',01');
 expect(formatEuro('1234.5').replace(/\./g,'')).toContain('1234,50');
 expect(formatEuro(0)).toContain('0,00');
});
