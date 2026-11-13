import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { describe, it, expect } from 'vitest';
import { GlassDirective } from './glass.directive';

@Component({
  standalone: true,
  imports: [GlassDirective],
  template: `
    <div id="comVidro" appGlass [glassBlur]="blur()" [glassTint]="tint()"></div>
    <div id="semVidro"></div>
  `,
})
class HostGlass {
  blur = signal<number | null>(null);
  tint = signal<string | null>(null);
}

describe('GlassDirective', () => {
  function montar() {
    const fixture = TestBed.configureTestingModule({ imports: [HostGlass] }).createComponent(HostGlass);
    fixture.detectChanges();
    const el = (id: string) => fixture.nativeElement.querySelector(`#${id}`) as HTMLElement;
    return { fixture, el };
  }

  it('so aplica no elemento marcado com appGlass', () => {
    const { el } = montar();
    expect(el('comVidro').classList.contains('lg-glass')).toBe(true);
    expect(el('semVidro').classList.contains('lg-glass')).toBe(false);
  });

  it('sem opcoes nao poe estilo inline', () => {
    const { el } = montar();
    expect(el('comVidro').getAttribute('style') ?? '').toBe('');
  });

  it('aplica desfoque e cor quando informados', () => {
    const { fixture, el } = montar();
    fixture.componentInstance.blur.set(8);
    fixture.componentInstance.tint.set('rgb(255 255 255 / 0.3)');
    fixture.detectChanges();
    expect(el('comVidro').style.getPropertyValue('--lg-blur')).toBe('8px');
    expect(el('comVidro').style.getPropertyValue('--lg-glass')).toBe('rgb(255 255 255 / 0.3)');
  });
});
