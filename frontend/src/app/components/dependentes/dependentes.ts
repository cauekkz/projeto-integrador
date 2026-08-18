import { Component, EventEmitter, OnInit, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Header } from '../header/header';
import { AddStudentService } from '../../services/addStudent.service';

@Component({
  selector: 'app-dependentes',
  standalone: true,
  imports: [CommonModule, Header],
  templateUrl: './dependentes.html',
  styleUrl: './dependentes.css',
})
export class Dependentes implements OnInit {
  @Output() fecharEvent = new EventEmitter<void>();
  @Output() adicionarEvent = new EventEmitter<void>();

  dependentes: any[] = [];
  carregando = true;

  constructor(private addStudentService: AddStudentService) {}

  ngOnInit() {
    this.carregarDependentes();
  }

  carregarDependentes() {
    this.carregando = true;
    this.addStudentService.getMyChildren().subscribe({
      next: (resposta: any) => {
        this.dependentes = resposta?.content ?? resposta ?? [];
        this.carregando = false;
      },
      error: () => {
        this.dependentes = [];
        this.carregando = false;
      },
    });
  }

  toggleDependente(dep: any) {
    dep.aberto = !dep.aberto;
  }

  fechar() {
    this.fecharEvent.emit();
  }

  adicionarDependente() {
    this.adicionarEvent.emit();
  }
}
