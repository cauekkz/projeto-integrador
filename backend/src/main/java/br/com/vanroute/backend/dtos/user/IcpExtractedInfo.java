package br.com.vanroute.backend.dtos.user;

public class IcpExtractedInfo {
    private String name;
    private String cpf;

    public IcpExtractedInfo(String name, String cpf) {
        this.name = name;
        this.cpf = cpf;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCpf() {
        return cpf;
    }

    public void setCpf(String cpf) {
        this.cpf = cpf;
    }
}
