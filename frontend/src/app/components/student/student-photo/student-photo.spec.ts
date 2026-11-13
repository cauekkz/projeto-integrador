import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { StudentPhoto } from './student-photo';
import { StudentService } from '../../../services/student.service';
import { PopUpService } from '../../../shared/pop-up';
import { ConfirmacaoService } from '../../../shared/confirmacao/confirmacao.service';

describe('Foto do aluno (photoKey do backend)', () => {
  let http: HttpTestingController;
  let popUp: { show: ReturnType<typeof vi.fn>; showError: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    popUp = { show: vi.fn(), showError: vi.fn() };
    TestBed.configureTestingModule({
      imports: [StudentPhoto],
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: PopUpService, useValue: popUp }],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    vi.unstubAllGlobals();
    TestBed.resetTestingModule();
  });

  describe('StudentService', () => {
    it('sobe a foto: pede o link, manda pro bucket e grava o fileKey no aluno', async () => {
      const fetchMock = vi.fn().mockResolvedValue({ ok: true, status: 200 });
      vi.stubGlobal('fetch', fetchMock);
      const service = TestBed.inject(StudentService);
      const file = new File(['img'], 'lucas.png', { type: 'image/png' });

      const promessa = service.uploadStudentPhoto('s-1', file);

      const link = http.expectOne((r) => r.url === '/api/student/s-1/photo-upload-url');
      expect(link.request.params.get('fileName')).toBe('lucas.png');
      expect(link.request.params.get('contentType')).toBe('image/png');
      link.flush({ uploadUrl: 'http://localhost:9000/vanroute/fotos/x.png?assinatura', fileKey: 'students/x.png' });
      await Promise.resolve();
      await Promise.resolve();

      const salvar = http.expectOne('/api/student/s-1/photo');
      expect(salvar.request.method).toBe('PUT');
      expect(salvar.request.body).toEqual({ fileKey: 'students/x.png' });
      salvar.flush({ photoKey: 'students/x.png' });

      await expect(promessa).resolves.toBe('students/x.png');
      // o PUT no bucket passa pelo proxy /storage
      expect(fetchMock.mock.calls[0][0]).toBe('/storage/vanroute/fotos/x.png?assinatura');
    });

    it('remove a foto com DELETE', () => {
      TestBed.inject(StudentService).removeStudentPhoto('s-1').subscribe();
      const req = http.expectOne('/api/student/s-1/photo');
      expect(req.request.method).toBe('DELETE');
      req.flush(null, { status: 204, statusText: 'No Content' });
    });

    it('troca photoKey por link e deixa null quem não tem foto', () => {
      let resultado: Array<{ id: string; photoUrl: string | null }> = [];
      TestBed.inject(StudentService)
        .comFotos([
          { id: 'a', photoKey: 'students/a.png' },
          { id: 'b', photoKey: null },
        ])
        .subscribe((lista) => (resultado = lista));

      http
        .expectOne((r) => r.url === '/api/storage/download-url' && r.params.get('fileKey') === 'students/a.png')
        .flush({ downloadUrl: 'http://localhost:9000/vanroute/students/a.png?x', fileKey: 'students/a.png' });

      expect(resultado.map((r) => [r.id, r.photoUrl])).toEqual([
        ['a', '/storage/vanroute/students/a.png?x'],
        ['b', null],
      ]);
    });

    it('admin = filhos do my-children com isAdmin=true', () => {
      let ids = new Set<string>();
      TestBed.inject(StudentService).getAdminChildIds().subscribe((s) => (ids = s));
      const req = http.expectOne((r) => r.url === '/api/student/my-children');
      expect(req.request.params.get('isAdmin')).toBe('true');
      req.flush({ content: [{ id: 's-1' }], last: true });
      expect([...ids]).toEqual(['s-1']);
    });
  });

  describe('StudentPhoto (componente)', () => {
    function criar(admin: boolean, photoUrl: string | null) {
      const fixture = TestBed.createComponent(StudentPhoto);
      fixture.componentRef.setInput('studentId', 's-1');
      fixture.componentRef.setInput('nome', 'Lucas Pereira');
      fixture.componentRef.setInput('admin', admin);
      fixture.componentRef.setInput('photoUrl', photoUrl);
      fixture.detectChanges();
      return fixture;
    }

    it('sem foto mostra as iniciais', () => {
      const el: HTMLElement = criar(false, null).nativeElement;
      expect(el.querySelector('.iniciais')?.textContent?.trim()).toBe('LP');
      expect(el.querySelector('img')).toBeNull();
    });

    it('quem não é admin não vê Trocar nem Remover', () => {
      const el: HTMLElement = criar(false, '/foto.png').nativeElement;
      expect(el.textContent).not.toContain('Trocar foto');
      expect(el.textContent).not.toContain('Remover foto');
    });

    it('admin vê Trocar foto; Remover só aparece com foto', () => {
      expect((criar(true, null).nativeElement as HTMLElement).textContent).not.toContain('Remover foto');
      const el: HTMLElement = criar(true, '/foto.png').nativeElement;
      expect(el.textContent).toContain('Trocar foto');
      expect(el.textContent).toContain('Remover foto');
    });

    it('remover avisa a tela com foto nula', async () => {
      const fixture = criar(true, '/foto.png');
      const emitido: unknown[] = [];
      fixture.componentInstance.fotoMudou.subscribe((v) => emitido.push(v));

      const pergunta = vi.spyOn(TestBed.inject(ConfirmacaoService), 'perguntar').mockResolvedValue(true);
      await fixture.componentInstance.remover();
      expect(pergunta).toHaveBeenCalledWith(expect.objectContaining({ titulo: 'Remover a foto?' }));
      http.expectOne('/api/student/s-1/photo').flush(null, { status: 204, statusText: 'No Content' });

      expect(emitido).toEqual([{ photoKey: null, photoUrl: null }]);
    });
  });
});
