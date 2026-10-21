package br.com.vanroute.backend.dtos.route.stops;

import br.com.vanroute.backend.models.shared.enums.WeekDay;

import java.util.Set;

// vazio ou ausente = todos os dias
public record UpdateWeekdaysRequestDTO(Set<WeekDay> weekdays) {
}
