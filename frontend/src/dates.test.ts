import {it,expect} from 'vitest';
import {dateInputToday} from './dates';
it('uses the user calendar day rather than the UTC day',()=>{
 expect(dateInputToday(new Date('2026-10-24T23:30:00Z'),'Europe/Rome')).toBe('2026-10-25');
 expect(dateInputToday(new Date('2026-10-24T23:30:00Z'),'America/New_York')).toBe('2026-10-24');
});
