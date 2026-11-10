import { Component, EventEmitter, Input, Output, inject } from '@angular/core';
import { StudentService } from '../../../services/student.service';
import { PopUpService } from '../../../shared/pop-up';
import { ConfirmacaoService } from '../../../shared/confirmacao/confirmacao.service';

// mesmo limite do backend para a foto do aluno
const FOTO_TAMANHO_MAXIMO = 10 * 1024 * 1024;

// foto do aluno com "Trocar foto" / "Remover foto" (Figma "Responsável · /student/:id · admin").
// Os links so aparecem pro admin; pros outros a foto e so leitura. Sem foto, iniciais
@Component({
  selector: 'app-student-photo',
  standalone: true,
  templateUrl: './student-photo.html',
  styleUrl: './student-photo.css',
})
export class StudentPhoto {
  @Input({ required: true }) studentId = '';
  @Input() nome = '';
  @Input() photoUrl: string | null = null;
  @Input() admin = false;
  // texto ao lado da foto (o detalhe do filho usa pra explicar o papel de admin)
  @Input() texto = '';

  // nova foto (link de leitura) ou null quando removida
  @Output() fotoMudou = new EventEmitter<{ photoKey: string | null; photoUrl: string | null }>();

  private studentService = inject(StudentService);
  private popUpService = inject(PopUpService);
  private confirmacao = inject(ConfirmacaoService);

  enviando = false;

  get iniciais(): string {
    return (this.nome ?? '')
      .split(' ')
      .filter(Boolean)
      .slice(0, 2)
      .map((parte) => parte[0].toUpperCase())
      .join('');
  }

  async selecionar(event: Event) {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file || this.enviando) return;

    if (!file.type.startsWith('image/')) {
      this.popUpService.show('A foto do aluno precisa ser uma imagem', 'error', 'Dados inválidos');
      return;
    }
    if (file.size > FOTO_TAMANHO_MAXIMO) {
      this.popUpService.show('O arquivo pode ter no máximo 10 MB', 'error', 'Dados inválidos');
      return;
    }

    this.enviando = true;
    try {
      const photoKey = await this.studentService.uploadStudentPhoto(this.studentId, file);
      // mostra o arquivo escolhido na hora; o link do bucket vale so 15 min e a proxima tela busca outro
      this.fotoMudou.emit({ photoKey, photoUrl: URL.createObjectURL(file) });
      this.popUpService.show('Foto do aluno atualizada.', 'success', 'Tudo certo');
    } catch (error) {
      // mensagem do backend quando tem (ex.: 403 de quem nao e admin); falha no bucket cai no texto padrao
      this.popUpService.showError(error, 'Não foi possível enviar a foto. Tente novamente.');
    } finally {
      this.enviando = false;
    }
  }

  async remover() {
    if (this.enviando) return;
    if (!(await this.confirmacao.perguntar({ titulo: 'Remover a foto?', texto: 'A foto do aluno é apagada.', acao: 'Remover foto' }))) return;
    if (this.enviando) return;
    this.enviando = true;
    this.studentService.removeStudentPhoto(this.studentId).subscribe({
      next: () => {
        this.enviando = false;
        this.fotoMudou.emit({ photoKey: null, photoUrl: null });
        this.popUpService.show('Foto do aluno removida.', 'success', 'Tudo certo');
      },
      // o popup global ja mostra o erro (ex.: 403 de quem nao e admin)
      error: () => (this.enviando = false),
    });
  }
}
