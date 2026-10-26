package br.com.vanroute.backend.models.shared;

import br.com.vanroute.backend.models.shared.enums.WeekDay;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

// guarda os dias como "MONDAY,WEDNESDAY" numa coluna texto
@Converter
public class WeekDaysConverter implements AttributeConverter<Set<WeekDay>, String> {

    @Override
    public String convertToDatabaseColumn(Set<WeekDay> weekdays) {
        return EnumSet.copyOf(weekdays).stream().map(Enum::name).collect(Collectors.joining(","));
    }

    @Override
    public Set<WeekDay> convertToEntityAttribute(String value) {
        if (value == null || value.isBlank()) {
            return EnumSet.allOf(WeekDay.class);
        }
        return Arrays.stream(value.split(","))
                .map(WeekDay::valueOf)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(WeekDay.class)));
    }
}
