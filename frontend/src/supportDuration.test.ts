import {it,expect} from 'vitest';import {supportDuration} from './supportDuration';
it('formats seconds consistently without wrapping daily or annual totals',()=>{for(const [seconds,text] of [[0,'00:00:00'],[59,'00:00:59'],[60,'00:01:00'],[3661,'01:01:01'],[360001,'100:00:01']] as const)expect(supportDuration(seconds)).toBe(text);});
