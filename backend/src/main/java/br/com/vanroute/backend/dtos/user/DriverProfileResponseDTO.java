package br.com.vanroute.backend.dtos.user;

import br.com.vanroute.backend.dtos.rating.RatingResponseDTO;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

// o que o responsavel ve do motorista: nada de CPF, e-mail ou telefone (a conversa e pelo chat)
public record DriverProfileResponseDTO(
        UUID driverId,
        String name,
        // foto: GET /api/storage/download-url?fileKey=...
        String photoKey,
        LocalDate memberSince,
        List<SchoolItem> schools,
        List<VehicleItem> vehicles,
        Double ratingAverage,
        long ratingCount,
        // a avaliacao de quem esta vendo (null se nao avaliou ou se nao e responsavel)
        RatingResponseDTO myRating,
        List<RatingResponseDTO> recentRatings
) {
    public record SchoolItem(UUID schoolId, String name) {
    }

    public record VehicleItem(String model, String color, String plate, Integer capacity) {
    }
}
