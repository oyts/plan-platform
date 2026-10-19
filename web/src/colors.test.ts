import { describe,it,expect } from 'vitest';
import { normalizeHex,hexToRgb,rgbToHex } from './colors';
describe('shared precise color input',()=>{
  it('normalizes HEX and round trips RGB including boundary values',()=>{expect(normalizeHex('abc')).toBe('#AABBCC');expect(hexToRgb('#00ff80')).toEqual([0,255,128]);expect(rgbToHex([0,255,128])).toBe('#00FF80');expect(rgbToHex(['0','0','0'])).toBe('#000000');expect(rgbToHex([255,255,255])).toBe('#FFFFFF');});
  it('rejects invalid or partial channels instead of clipping or changing stored color',()=>{for(const values of [[-1,0,0],[256,0,0],['',0,0],[1.5,0,0],['1e2',0,0]])expect(rgbToHex(values)).toBeNull();expect(normalizeHex('#12345z')).toBeNull();});
});
