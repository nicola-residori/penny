import {it,expect} from 'vitest';
import {parseSpan,bucketCount,endOfPeriod,periodLabel} from './analyticsSpan';
it('validates date-only units and calendar size limits',()=>{
 for(const invalid of ['0d','01d','-1d','1m','2h','1.5w','10000d','201y','2401mon'])expect(parseSpan(invalid)).toBeNull();
 expect(parseSpan(' 2D ')?.canonical).toBe('2d');expect(parseSpan('200y')?.n).toBe(200);expect(parseSpan('2400mon')?.n).toBe(2400);
});
it('counts actual aligned buckets including leap and negative epoch boundaries',()=>{
 expect(bucketCount('2024-02-28','2024-03-03',parseSpan('2d')!)).toBe(3);
 expect(bucketCount('1969-12-31','1970-01-01',parseSpan('2d')!)).toBe(2);
 expect(bucketCount('1969-12-31','1970-01-04',parseSpan('2w')!)).toBe(1);
 expect(bucketCount('1969-12-31','1970-01-05',parseSpan('2w')!)).toBe(2);
 expect(bucketCount('2024-02-29','2024-04-01',parseSpan('3mon')!)).toBe(2);
 expect(endOfPeriod('2024-02-29',parseSpan('2d')!)).toBe('2024-03-01');
});
it('labels custom day and multi-month ranges without treating them as years',()=>{
 expect(periodLabel('2024-02-29','2d','2024-03-01')).toContain('2024');expect(periodLabel('2024-02-29','2d','2024-03-01')).toContain('mar');
 expect(periodLabel('1968-01-01','2y','1969-12-31')).toBe('1968–1969');expect(periodLabel('2024-01-01','3mon','2024-03-31')).toContain('mar');
});
