import { describe, it, expect } from 'vitest';
import {
  limparMascara,
  formatarCpf,
  formatarTelefone,
  formatarCep,
  formatarData,
  dataParaIso,
} from './mask.utils';

describe('mask.utils', () => {
  describe('limparMascara', () => {
    it('should clean CPF mask correctly (123.456.789-00 -> 12345678900)', () => {
      expect(limparMascara('123.456.789-00')).toBe('12345678900');
    });

    it('should clean Telefone mask correctly ((11) 98765-4321 -> 11987654321)', () => {
      expect(limparMascara('(11) 98765-4321')).toBe('11987654321');
    });

    it('should clean CEP mask correctly (04165-011 -> 04165011)', () => {
      expect(limparMascara('04165-011')).toBe('04165011');
    });

    it('should preserve leading zeroes and not convert to number', () => {
      expect(limparMascara('012.345.678-90')).toBe('01234567890');
      expect(limparMascara('04165-011')).toBe('04165011');
      expect(typeof limparMascara('012.345.678-90')).toBe('string');
      expect(limparMascara('000.000.000-00')).toBe('00000000000');
    });

    it('should handle null, undefined and empty string gracefully', () => {
      expect(limparMascara(null)).toBe('');
      expect(limparMascara(undefined)).toBe('');
      expect(limparMascara('')).toBe('');
    });
  });

  describe('formatarCpf', () => {
    it('should format 11 digits as 000.000.000-00', () => {
      expect(formatarCpf('12345678900')).toBe('123.456.789-00');
    });

    it('should format partial CPF digits progressively', () => {
      expect(formatarCpf('123')).toBe('123');
      expect(formatarCpf('1234')).toBe('123.4');
      expect(formatarCpf('123456')).toBe('123.456');
      expect(formatarCpf('1234567')).toBe('123.456.7');
      expect(formatarCpf('123456789')).toBe('123.456.789');
      expect(formatarCpf('1234567890')).toBe('123.456.789-0');
    });

    it('should handle empty or null input', () => {
      expect(formatarCpf('')).toBe('');
      expect(formatarCpf(null)).toBe('');
    });
  });

  describe('formatarTelefone', () => {
    it('should format 11 digits (mobile) as (00) 00000-0000', () => {
      expect(formatarTelefone('11987654321')).toBe('(11) 98765-4321');
    });

    it('should format 10 digits (landline) as (00) 0000-0000', () => {
      expect(formatarTelefone('1123456789')).toBe('(11) 2345-6789');
    });

    it('should format partial phone digits progressively', () => {
      expect(formatarTelefone('11')).toBe('(11');
      expect(formatarTelefone('119')).toBe('(11) 9');
      expect(formatarTelefone('119876')).toBe('(11) 9876');
      expect(formatarTelefone('11987654')).toBe('(11) 9876-54');
    });
  });

  describe('formatarCep', () => {
    it('should format 8 digits as 00000-000', () => {
      expect(formatarCep('04165011')).toBe('04165-011');
    });

    it('should format partial CEP digits', () => {
      expect(formatarCep('04165')).toBe('04165');
      expect(formatarCep('041650')).toBe('04165-0');
    });
  });

  describe('formatarData', () => {
    it('should format 8 digits as DD/MM/AAAA', () => {
      expect(formatarData('15032012')).toBe('15/03/2012');
    });

    it('should format partial date digits', () => {
      expect(formatarData('15')).toBe('15');
      expect(formatarData('1503')).toBe('15/03');
      expect(formatarData('150320')).toBe('15/03/20');
    });
  });

  describe('dataParaIso', () => {
    it('should convert DD/MM/AAAA to AAAA-MM-DD', () => {
      expect(dataParaIso('15/03/2012')).toBe('2012-03-15');
    });

    it('should convert DDMMAAAA to AAAA-MM-DD', () => {
      expect(dataParaIso('15032012')).toBe('2012-03-15');
    });

    it('should return empty string for null or empty input', () => {
      expect(dataParaIso('')).toBe('');
      expect(dataParaIso(null)).toBe('');
    });
  });
});
