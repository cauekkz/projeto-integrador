import { Injectable } from '@angular/core';

const PREFIXO = 'vanroute.fotoDependente.';
// lado maior da miniatura: pequena o bastante pra caber varias no localStorage
const LADO_MAX_PX = 256;

// foto do dependente
// PROVISORIO: o backend ainda nao guarda foto de aluno, entao a miniatura fica no localStorage deste navegador
// (em outro aparelho/navegador nao aparece). Quando existir rota de foto de aluno, trocar so este servico.
@Injectable({
  providedIn: 'root',
})
export class StudentPhotoService {
  obter(studentId: string | null | undefined): string | null {
    if (!studentId) return null;
    try {
      return localStorage.getItem(PREFIXO + studentId);
    } catch {
      return null;
    }
  }

  // reduz a imagem antes de guardar; devolve false se nao conseguiu (navegador sem espaco/privado)
  async salvar(studentId: string, arquivo: File): Promise<boolean> {
    try {
      const miniatura = await this.reduzir(arquivo);
      localStorage.setItem(PREFIXO + studentId, miniatura);
      return true;
    } catch (error) {
      console.error('Não foi possível guardar a foto do dependente:', error);
      return false;
    }
  }

  private async reduzir(arquivo: File): Promise<string> {
    const bitmap = await createImageBitmap(arquivo);
    const escala = Math.min(1, LADO_MAX_PX / Math.max(bitmap.width, bitmap.height));
    const canvas = document.createElement('canvas');
    canvas.width = Math.round(bitmap.width * escala);
    canvas.height = Math.round(bitmap.height * escala);
    const ctx = canvas.getContext('2d');
    if (!ctx) throw new Error('Canvas indisponível');
    ctx.drawImage(bitmap, 0, 0, canvas.width, canvas.height);
    bitmap.close();
    return canvas.toDataURL('image/jpeg', 0.82);
  }
}
