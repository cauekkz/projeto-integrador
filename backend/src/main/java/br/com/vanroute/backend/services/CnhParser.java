package br.com.vanroute.backend.services;

import br.com.vanroute.backend.exceptions.InvalidDocumentException;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// le os dados da CNH digital a partir do texto do OCR e confere as regras do motorista de escolar.
// Data de nascimento, validade e nome vem da zona de leitura de maquina (as 3 linhas com "<<<" no rodape, padrao
// ICAO): as datas tem digito verificador, entao um erro do OCR ali e detectado em vez de virar uma data errada.
// A categoria vem do campo "CAT HAB", logo depois do CPF e do numero de registro.
// O curso de condutor de escolar e a autorizacao do municipio nao estao na CNH: nao da pra conferir por aqui
public final class CnhParser {

    // CTB art. 138: condutor de escolar tem 21 anos ou mais e categoria D (a E inclui a D)
    static final int MIN_AGE = 21;

    public record CnhData(String cpf, String name, boolean nameTruncated, LocalDate birthDate, LocalDate expiryDate,
                          String category) {

        public boolean complete() {
            return cpf != null && name != null && birthDate != null && expiryDate != null && category != null;
        }

        // junta duas leituras do OCR: fica com o que a primeira leu e completa com a segunda
        public CnhData orElse(CnhData other) {
            return new CnhData(cpf != null ? cpf : other.cpf, name != null ? name : other.name,
                    name != null ? nameTruncated : other.nameTruncated, birthDate != null ? birthDate : other.birthDate,
                    expiryDate != null ? expiryDate : other.expiryDate, category != null ? category : other.category);
        }
    }

    // linha 2 da MRZ: nascimento AAMMDD + digito, sexo, validade AAMMDD + digito, nacionalidade
    private static final Pattern MRZ_DATES = Pattern.compile("(\\d{6})(\\d)([MF<])(\\d{6})(\\d)BRA");
    // linha 3 da MRZ: o nome com "<" no lugar dos espacos
    private static final Pattern MRZ_NAME = Pattern.compile("^[A-Z]+(<{1,2}[A-Z]+)+<*$");
    // CPF, numero de registro (9 a 11 digitos) e a categoria logo depois
    private static final Pattern CATEGORY = Pattern.compile(
            "\\d{3}\\.?\\d{3}\\.?\\d{3}-?\\d{2}[^0-9A-Z]{0,8}\\d{9,11}[^0-9A-Z]{0,6}(A[BCDE]|[ABCDE])\\b");
    private static final Pattern CPF = Pattern.compile("(?<!\\d)(\\d{3})[.\\s-]?(\\d{3})[.\\s-]?(\\d{3})[.\\s-]?(\\d{2})(?!\\d)");
    private static final int MRZ_LINE_LENGTH = 30;

    private CnhParser() {
    }

    public static CnhData parse(String text) {
        if (text == null) {
            return new CnhData(null, null, false, null, null, null);
        }
        LocalDate birth = null;
        LocalDate expiry = null;
        String name = null;
        boolean truncated = false;
        for (String rawLine : text.split("\\R")) {
            String line = rawLine.replace(" ", "").toUpperCase(Locale.ROOT);
            if (birth == null && line.contains("BRA")) {
                // OCR troca O por 0 e I/L por 1 nos campos numericos
                Matcher m = MRZ_DATES.matcher(line.replace('O', '0').replace('I', '1'));
                if (m.find() && checkDigit(m.group(1)) == digit(m.group(2)) && checkDigit(m.group(4)) == digit(m.group(5))) {
                    birth = mrzDate(m.group(1), true);
                    expiry = mrzDate(m.group(4), false);
                }
            }
            if (MRZ_NAME.matcher(line).matches() && line.contains("<<")) {
                name = line.replaceAll("<+", " ").trim();
                truncated = line.length() >= MRZ_LINE_LENGTH && !line.endsWith("<");
            }
        }
        Matcher category = CATEGORY.matcher(text.toUpperCase(Locale.ROOT));
        return new CnhData(cpf(text), name, truncated, birth, expiry, category.find() ? category.group(1) : null);
    }

    // regras do motorista de escolar; erro com mensagem pra tela de cadastro
    public static void check(CnhData cnh, String typedName, LocalDate today) {
        if (cnh.cpf() == null) {
            throw new InvalidDocumentException("Não foi possível ler o CPF da CNH. Envie o PDF original da CNH digital");
        }
        if (cnh.name() == null || cnh.birthDate() == null || cnh.expiryDate() == null) {
            throw new InvalidDocumentException("Não foi possível ler o nome e as datas da CNH. Envie o PDF original da CNH digital");
        }
        if (!sameName(cnh.name(), cnh.nameTruncated(), typedName)) {
            throw new InvalidDocumentException("O nome informado não é o mesmo da CNH. Use o nome completo como está na CNH");
        }
        if (cnh.expiryDate().isBefore(today)) {
            throw new InvalidDocumentException("A CNH está vencida");
        }
        if (Period.between(cnh.birthDate(), today).getYears() < MIN_AGE) {
            throw new InvalidDocumentException("O motorista de transporte escolar precisa ter pelo menos " + MIN_AGE + " anos");
        }
        if (cnh.category() == null) {
            throw new InvalidDocumentException("Não foi possível ler a categoria da CNH. Envie o PDF original da CNH digital");
        }
        if (!cnh.category().contains("D") && !cnh.category().contains("E")) {
            throw new InvalidDocumentException("Transporte escolar exige CNH categoria D ou E (a da CNH enviada é " + cnh.category() + ")");
        }
    }

    // compara sem acento, maiuscula/minuscula e ordem das palavras. A MRZ corta nomes longos (30 caracteres):
    // nesse caso a ultima palavra lida pode estar pela metade
    static boolean sameName(String cnhName, boolean truncated, String typedName) {
        if (typedName == null) {
            return false;
        }
        List<String> cnh = words(cnhName);
        List<String> typed = words(typedName);
        if (!truncated) {
            return sorted(cnh).equals(sorted(typed));
        }
        if (cnh.isEmpty()) {
            return false;
        }
        List<String> whole = cnh.subList(0, cnh.size() - 1);
        String partial = cnh.get(cnh.size() - 1);
        List<String> rest = new ArrayList<>(typed);
        for (String word : whole) {
            if (!rest.remove(word)) {
                return false;
            }
        }
        return rest.stream().anyMatch(word -> word.startsWith(partial));
    }

    private static List<String> words(String name) {
        String plain = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toUpperCase(Locale.ROOT).replaceAll("[^A-Z ]", " ").trim();
        return plain.isEmpty() ? List.of() : Arrays.asList(plain.split("\\s+"));
    }

    private static List<String> sorted(List<String> words) {
        return words.stream().sorted().toList();
    }

    private static String cpf(String text) {
        Matcher matcher = CPF.matcher(text);
        while (matcher.find()) {
            String cpf = matcher.group(1) + matcher.group(2) + matcher.group(3) + matcher.group(4);
            if (validCpf(cpf)) {
                return cpf;
            }
        }
        return null;
    }

    // digitos verificadores do CPF: um numero lido errado pelo OCR quase sempre falha aqui
    static boolean validCpf(String cpf) {
        if (cpf.length() != 11 || cpf.chars().distinct().count() == 1) {
            return false;
        }
        for (int position = 9; position <= 10; position++) {
            int sum = 0;
            for (int i = 0; i < position; i++) {
                sum += (cpf.charAt(i) - '0') * (position + 1 - i);
            }
            int expected = (sum * 10) % 11 % 10;
            if (cpf.charAt(position) - '0' != expected) {
                return false;
            }
        }
        return true;
    }

    // digito verificador ICAO 9303: pesos 7, 3, 1
    static int checkDigit(String digits) {
        int[] weights = {7, 3, 1};
        int sum = 0;
        for (int i = 0; i < digits.length(); i++) {
            sum += (digits.charAt(i) - '0') * weights[i % 3];
        }
        return sum % 10;
    }

    private static int digit(String s) {
        return s.charAt(0) - '0';
    }

    // AAMMDD: nascimento no futuro e do seculo passado; validade e sempre deste seculo
    private static LocalDate mrzDate(String yymmdd, boolean birth) {
        int yy = Integer.parseInt(yymmdd.substring(0, 2));
        int month = Integer.parseInt(yymmdd.substring(2, 4));
        int day = Integer.parseInt(yymmdd.substring(4, 6));
        int year = 2000 + yy;
        if (birth && year > LocalDate.now(TripService.ZONE).getYear()) {
            year -= 100;
        }
        try {
            return LocalDate.of(year, month, day);
        } catch (java.time.DateTimeException e) {
            return null;
        }
    }
}
