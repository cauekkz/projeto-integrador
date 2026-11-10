import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, Router } from '@angular/router';
import { TrailHeader } from '../../../shared/trail-header/trail-header';
import { Footer } from '../../../shared/footer/footer';
import { PopUpService } from '../../../shared/pop-up';
import { GroupService } from '../../../services/group.service';
import { ContractService } from '../../../services/contract.service';
import { StudentService } from '../../../services/student.service';
import { driverGroup } from '../../../services/dto/group/group.dto';
import { iniciaisDe } from '../../../shared/iniciais/iniciais';

type Membro = driverGroup['students'][number] & { photoUrl: string | null };

// folhas da tela (Figma 1058:1116 renomear, 1058:1187 apagar, 1058:1255 avisar)
type Folha = 'renomear' | 'apagar' | 'avisar' | 'adicionar' | { tirar: Membro };

// Figma "Motorista · /driver-groups/:id" (1058:904 com alunos, 1058:964 sem alunos)
@Component({
  selector: 'app-driver-group',
  standalone: true,
  imports: [FormsModule, TrailHeader, Footer],
  templateUrl: './driver-group.html',
  styleUrls: ['../../../shared/folha.css', '../../../shared/rota.css', './driver-group.css'],
})
export class DriverGroup implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private groupService = inject(GroupService);
  private contractService = inject(ContractService);
  private studentService = inject(StudentService);
  private popUpService = inject(PopUpService);

  private groupId = '';
  protected carregando = signal(true);
  protected nome = signal('');
  protected alunos = signal<Membro[]>([]);

  protected folha = signal<Folha | null>(null);
  protected enviando = signal(false);
  protected nomeNovo = '';
  protected motivo = '';

  // "Adicionar": alunos com contrato ativo que ainda nao estao no grupo
  protected candidatos = signal<{ id: string; nome: string }[] | null>(null);
  protected escolhido = signal<string | null>(null);

  protected tirando = computed(() => {
    const f = this.folha();
    return f && typeof f === 'object' ? f.tirar : null;
  });

  ngOnInit() {
    this.groupId = this.route.snapshot.paramMap.get('groupId') ?? '';
    this.carregar();
  }

  // o back nao tem GET de um grupo: busca a lista e acha esse
  private carregar() {
    this.groupService.getGroups().subscribe({
      next: (lista) => {
        const grupo = lista.find((g) => g.id === this.groupId);
        if (!grupo) {
          this.popUpService.show('Esse grupo não existe mais.', 'warning', 'Grupo não encontrado');
          this.router.navigate(['/driver-groups']);
          return;
        }
        this.aplicar(grupo);
        this.carregando.set(false);
      },
      error: () => {
        this.popUpService.show('Não foi possível carregar o grupo. Tente de novo em instantes.', 'error', 'Erro');
        this.router.navigate(['/driver-groups']);
      },
    });
  }

  private aplicar(grupo: driverGroup) {
    this.nome.set(grupo.name);
    const ordenados = [...grupo.students].sort((a, b) => a.studentName.localeCompare(b.studentName));
    this.studentService.comFotos(ordenados).subscribe((lista) => this.alunos.set(lista));
  }

  voltar() {
    this.router.navigate(['/driver-groups']);
  }

  iniciais(nome: string | null | undefined): string {
    return iniciaisDe(nome);
  }

  abrir(folha: Folha) {
    if (folha === 'renomear') this.nomeNovo = this.nome();
    if (folha === 'avisar') this.motivo = '';
    if (folha === 'adicionar') this.carregarCandidatos();
    this.folha.set(folha);
  }

  fechar() {
    if (!this.enviando()) this.folha.set(null);
  }

  private carregarCandidatos() {
    this.candidatos.set(null);
    this.escolhido.set(null);
    const noGrupo = new Set(this.alunos().map((a) => a.studentId));
    this.contractService.getMyContracts({ status: 'ACTIVE', size: 100 }).subscribe({
      next: (pagina) => {
        const unicos = new Map<string, string>();
        for (const c of pagina.content ?? []) if (!noGrupo.has(c.studentId)) unicos.set(c.studentId, c.studentName);
        const lista = [...unicos].map(([id, nome]) => ({ id, nome })).sort((a, b) => a.nome.localeCompare(b.nome));
        this.candidatos.set(lista);
        this.escolhido.set(lista[0]?.id ?? null);
      },
      error: () => this.candidatos.set([]),
    });
  }

  private aviso(erro: HttpErrorResponse) {
    const titulo = erro?.status === 403 ? 'Sem permissão' : 'Não foi possível concluir';
    this.popUpService.show(erro?.error?.message || 'Tente de novo em instantes.', erro?.status === 403 ? 'error' : 'warning', titulo);
  }

  // ---------- acoes ----------

  renomear() {
    const nome = this.nomeNovo.trim();
    if (this.enviando()) return;
    if (!nome) {
      this.popUpService.show('Dê um nome ao grupo.', 'warning', 'Campos Incompletos');
      return;
    }
    this.enviando.set(true);
    this.groupService.renameGroup(this.groupId, nome).subscribe({
      next: (grupo) => {
        this.enviando.set(false);
        this.folha.set(null);
        this.nome.set(grupo.name);
      },
      error: (erro: HttpErrorResponse) => {
        this.enviando.set(false);
        this.aviso(erro);
      },
    });
  }

  apagar() {
    if (this.enviando()) return;
    this.enviando.set(true);
    this.groupService.deleteGroup(this.groupId).subscribe({
      next: () => {
        this.enviando.set(false);
        this.folha.set(null);
        this.popUpService.show(`O grupo ${this.nome()} foi apagado.`, 'success', 'Grupo apagado');
        this.router.navigate(['/driver-groups']);
      },
      error: (erro: HttpErrorResponse) => {
        this.enviando.set(false);
        this.aviso(erro);
      },
    });
  }

  adicionar() {
    const studentId = this.escolhido();
    if (this.enviando() || !studentId) return;
    this.enviando.set(true);
    this.groupService.addStudent(this.groupId, studentId).subscribe({
      next: (grupo) => {
        this.enviando.set(false);
        this.folha.set(null);
        this.aplicar(grupo);
      },
      error: (erro: HttpErrorResponse) => {
        this.enviando.set(false);
        this.aviso(erro);
      },
    });
  }

  tirar(aluno: Membro) {
    if (this.enviando()) return;
    this.enviando.set(true);
    this.groupService.removeStudent(this.groupId, aluno.studentId).subscribe({
      next: (grupo) => {
        this.enviando.set(false);
        this.folha.set(null);
        this.aplicar(grupo);
      },
      error: (erro: HttpErrorResponse) => {
        this.enviando.set(false);
        this.aviso(erro);
      },
    });
  }

  avisar() {
    if (this.enviando()) return;
    this.enviando.set(true);
    this.groupService.notify(this.groupId, this.motivo.trim()).subscribe({
      next: (r) => {
        this.enviando.set(false);
        this.folha.set(null);
        const n = r.notifiedResponsibles;
        this.popUpService.show(n === 1 ? '1 responsável avisado.' : `${n} responsáveis avisados.`, 'success', 'Aviso enviado');
      },
      error: (erro: HttpErrorResponse) => {
        this.enviando.set(false);
        this.aviso(erro);
      },
    });
  }
}
