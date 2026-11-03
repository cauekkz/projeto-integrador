package br.com.vanroute.backend.models.document.enums;

// hoje so o PDF do contrato e guardado. CNH e CRLV sao validados no cadastro mas nao sao guardados;
// os outros documentos do motorista ficam comentados ate o app passar a guarda-los
public enum DocumentType {
    // CNH, // Cnh
    // SCHOOL_TRANSPORT_COURSE, // Curso Transporte Escolar
    // CRLV, // Crlv
    TRANSPORT_CONTRACT, // Contrato Transporte
    // SCHOOL_AUTHORIZATION, // Autorizacao Escola
    // LIABILITY_TERM, // Termo Responsabilidade
    // OTHER // Outro
}
