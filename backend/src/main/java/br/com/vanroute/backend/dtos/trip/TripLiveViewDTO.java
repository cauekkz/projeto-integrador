package br.com.vanroute.backend.dtos.trip;

import br.com.vanroute.backend.models.trip.enums.TripStatus;

import java.util.List;
import java.util.UUID;

// o que vai pelo WebSocket pra cada um. mode diz o que desenhar:
//   DRIVER    motorista: tudo
//   FOLLOWING responsavel: a van esta indo pro ponto do filho (casa ou escola): van, linha ate o ponto e tempo
//   ON_BOARD  responsavel: o filho esta na van, mas ela ainda vai buscar outros: so a van na rua (sem linha e sem tempo)
//   DONE      responsavel: os filhos dele ja foram entregues (ou faltaram) e a rota segue pros outros: acompanhamento encerrado
//   STATUS    responsavel: so o status dos filhos
public record TripLiveViewDTO(
        String type,
        UUID tripId,
        String routeName,
        TripStatus status,
        boolean following,
        String mode,
        Double latitude,
        Double longitude,
        Long positionAt,
        UUID targetStopId,
        Double etaSeconds,
        List<List<Double>> path,
        List<TripStudentDTO> students,
        List<TripLiveStopDTO> stops
) {
}
