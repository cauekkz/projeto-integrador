import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
import { describe, it, expect, afterEach, vi } from 'vitest';
import { TrailHeader } from './trail-header';

describe('Trilha do topo', () => {
  let http: HttpTestingController;

  afterEach(() => {
    for (const req of http.match(() => true)) if (!req.cancelled) req.flush({});
    http.verify();
    TestBed.resetTestingModule();
  });

  function criar(trilha: string[], links?: (string | null)[]) {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])] });
    http = TestBed.inject(HttpTestingController);
    const navegar = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    const fixture = TestBed.createComponent(TrailHeader);
    fixture.componentRef.setInput('trilha', trilha);
    if (links) fixture.componentRef.setInput('links', links);
    fixture.detectChanges();
    return { fixture, navegar };
  }

  it('"Motorista" leva pra home do motorista e o item atual não é botão', () => {
    const { fixture, navegar } = criar(['Motorista', 'Contratos']);
    const links = fixture.nativeElement.querySelectorAll('.migalha-link') as NodeListOf<HTMLButtonElement>;
    expect([...links].map((b) => b.textContent?.trim())).toEqual(['Motorista']);
    links[0].click();
    expect(navegar).toHaveBeenCalledWith('/driver-home');
  });

  it('"Responsável" e "Grupos" vão pras páginas deles; nome sem destino fica só texto', () => {
    const { fixture, navegar } = criar(['Responsável', 'Grupos', 'Desconhecido', 'Atual']);
    const links = fixture.nativeElement.querySelectorAll('.migalha-link') as NodeListOf<HTMLButtonElement>;
    expect(links.length).toBe(2);
    links[0].click();
    links[1].click();
    expect(navegar).toHaveBeenCalledWith('/responsible-home');
    expect(navegar).toHaveBeenCalledWith('/driver-groups');
  });

  it('links passados pela tela mandam no destino', () => {
    const { fixture, navegar } = criar(['Motorista', 'Atual'], ['/outro', null]);
    (fixture.nativeElement.querySelector('.migalha-link') as HTMLButtonElement).click();
    expect(navegar).toHaveBeenCalledWith('/outro');
  });
});
