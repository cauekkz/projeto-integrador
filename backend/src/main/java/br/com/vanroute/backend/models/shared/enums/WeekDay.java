package br.com.vanroute.backend.models.shared.enums;

public enum WeekDay {
    MONDAY, // Segunda
    TUESDAY, // Terça
    WEDNESDAY, // Quarta
    THURSDAY, // Quinta
    FRIDAY, // Sexta
    SATURDAY, // Sábado
    SUNDAY; // Domingo

    // nome em portugues pras mensagens de erro ("sem embarque na sexta")
    public String label() {
        return switch (this) {
            case MONDAY -> "segunda";
            case TUESDAY -> "terça";
            case WEDNESDAY -> "quarta";
            case THURSDAY -> "quinta";
            case FRIDAY -> "sexta";
            case SATURDAY -> "sábado";
            case SUNDAY -> "domingo";
        };
    }

    // "segunda, quarta e sexta" (na ordem da semana)
    public static String labels(java.util.Collection<WeekDay> days) {
        java.util.List<String> names = days.stream().sorted().map(WeekDay::label).toList();
        if (names.size() <= 1) {
            return String.join("", names);
        }
        return String.join(", ", names.subList(0, names.size() - 1)) + " e " + names.get(names.size() - 1);
    }
}