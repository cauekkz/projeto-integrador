# VanRoute

> Sistema Inteligente de Gestão e Monitoramento de Transporte Escolar

## 📖 Sobre o Projeto

O **VanRoute** é uma plataforma Full Stack desenvolvida para modernizar a gestão do transporte escolar. O sistema conecta **pais e responsáveis**, **motoristas**, **empresas de transporte** e futuramente **escolas**, oferecendo monitoramento em tempo real, gestão financeira, comunicação instantânea e otimização de rotas.

## 🎯 Objetivos

- Aumentar a segurança dos alunos.
- Permitir acompanhamento da van em tempo real.
- Automatizar processos administrativos e financeiros.
- Melhorar a comunicação entre responsáveis, motoristas e empresas.
- Gerar rotas inteligentes para reduzir tempo e custos.

## 🌎 ODS

O projeto está alinhado à **ODS 11 – Cidades e Comunidades Sustentáveis**, especialmente à meta **11.2**, promovendo transporte seguro, acessível e eficiente.

---

# Funcionalidades

## Área dos Pais

- Localização da van em tempo real
- Histórico de viagens
- Notificações de embarque e desembarque
- Aviso quando a van estiver próxima
- Pagamento de mensalidades
- Histórico financeiro
- Consulta de presença
- Comunicação com motorista
- Recebimento de ocorrências

## Área do Motorista

- Login seguro
- Dashboard operacional
- Dashboard financeiro
- Lista de passageiros
- Confirmação de embarque e desembarque
- Rota inteligente
- Registro de ocorrências
- Comunicação com responsáveis
- Gestão de contratos

## Área Administrativa

- Aprovação de motoristas
- Validação de documentos
- Gestão de empresas
- Gestão de pagamentos
- Avaliações dos responsáveis
- Relatórios
- Suspensão/Bloqueio de motoristas
- Monitoramento das viagens

## Futuramente

- Integração direta com escolas
- Calendário escolar
- Vinculação entre escolas, vans e alunos

---

# 🏗 Arquitetura

```text
Angular
   │
REST API
   │
Spring Boot
   │
PostgreSQL
```

---

# 🛠 Tecnologias

## Backend

- Java 21
- Spring Boot
- Spring Security
- JWT
- Spring Data JPA
- Hibernate
- Flyway
- PostgreSQL
- Maven

## Frontend

- Angular
- TypeScript
- Angular Material
- RxJS
- SCSS

---

# 📁 Estrutura

```text
vanroute/
├── backend/
├── frontend/
└── README.md
```

---

# ⚙️ Configuração do Ambiente

## Pré-requisitos

### Backend

- Java 21
- Maven
- PostgreSQL

### Frontend

- Node.js 22+
- Angular CLI

---

# 📥 Clonando o Projeto

```bash
git clone https://github.com/SEU-USUARIO/vanroute.git
cd vanroute
```

---

# 🗄 Banco de Dados

Criar banco:

```sql
CREATE DATABASE vanroute;
```


---

# Backend

## application.properties

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/vanroute
spring.datasource.username=vanroute
spring.datasource.password=123456

spring.jpa.hibernate.ddl-auto=validate
spring.flyway.enabled=true
```

## Instalar dependências

```bash
cd backend
mvn clean install
```

## Executar

```bash
mvn spring-boot:run
```

---

# Flyway

As migrations ficam em:

```text
src/main/resources/db/migration
```

Exemplo:

```text
V1__create_users.sql
V2__create_driver.sql
V3__create_candidate.sql
```

Nunca altere uma migration já executada. Sempre crie uma nova versão.

---

# Frontend

Instalar Angular CLI:

```bash
npm install -g @angular/cli
```

Instalar dependências:

```bash
cd frontend
npm install
```

Executar:

```bash
ng serve
```

Aplicação:

http://localhost:4200

---

# 🤝 Como Contribuir


1. Clone seu Fork.
2. Crie uma branch.

```bash
git checkout -b feature/nova-funcionalidade
```

4. Desenvolva.
5. Commit.

```bash
git commit -m "feat: adiciona cadastro de motorista"
```

6. Push.

```bash
git push origin feature/nova-funcionalidade
```

7. Abra um Pull Request.

---

# 🌳 Fluxo Git

- main → Produção
- develop → Desenvolvimento
- feature/* → Novas funcionalidades
- hotfix/* → Correções urgentes

---

# 📝 Convenção de Commits

- feat
- fix
- docs
- refactor
- style
- test
- chore

---

# 📌 Roadmap

- [ ] Aplicativo Android
- [ ] Aplicativo iOS
- [ ] IA para previsão de atrasos
- [ ] Reconhecimento facial
- [ ] QR Code de embarque
- [ ] Integração com escolas
- [ ] Split automático de pagamentos
- [ ] Notificações Push

---

# 📄 Licença

Este projeto é destinado para fins acadêmicos e poderá ser licenciado futuramente.

---

## 👥 Colaboradores

Antes de iniciar o desenvolvimento:

1. Clone o projeto.
2. Configure o PostgreSQL.
3. Configure o `application.properties`.
4. Execute as migrations do Flyway.
5. Inicie o backend.
6. Inicie o frontend.
7. Crie uma branch a partir de `develop`.
8. Desenvolva e envie um Pull Request.

Bom desenvolvimento! 🚐
