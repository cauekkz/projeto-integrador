package br.com.vanroute.backend.services;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// "exportar meus dados" (LGPD): tudo que o app guarda da pessoa, num JSON. Uma vez a cada 5 minutos
// (a consulta junta muitas tabelas). Nao inclui a senha nem os dados pessoais de outras pessoas
@Service
public class DataExportService {

    static final Duration COOLDOWN = Duration.ofMinutes(5);

    private final JdbcTemplate jdbc;
    private final RedisTemplate<String, String> redisTemplate;

    public DataExportService(JdbcTemplate jdbc, RedisTemplate<String, String> redisTemplate) {
        this.jdbc = jdbc;
        this.redisTemplate = redisTemplate;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> export(UUID userId) {
        String key = "export:cooldown:" + userId;
        if (!Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(key, "1", COOLDOWN))) {
            Long seconds = redisTemplate.getExpire(key);
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Você já exportou há pouco. Tente de novo em " + (seconds == null || seconds < 1 ? 300 : seconds) + " segundos");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("exportedAt", LocalDateTime.now(TripService.ZONE).toString());
        data.put("account", one("SELECT id, name, cpf, email, phone, role, status, created_at, photo_key FROM users WHERE id = ?", userId));
        data.put("termsAcceptances", list("SELECT version, accepted_at FROM terms_acceptances WHERE user_id = ? ORDER BY accepted_at", userId));

        // responsavel: cobranca, filhos (com enderecos e faltas), avaliacoes que deu
        data.put("billingAddress", one("""
                SELECT a.street, a.number, a.neighborhood, a.city, a.state, a.zip_code
                FROM responsibles r JOIN addresses a ON a.id = r.address_id WHERE r.user_id = ?""", userId));
        List<Map<String, Object>> children = list("""
                SELECT s.id, s.name, s.birth_date, sr.relation_type, sr.is_admin
                FROM student_responsibles sr JOIN students s ON s.id = sr.student_id
                WHERE sr.responsible_id = ? ORDER BY s.name""", userId);
        for (Map<String, Object> child : children) {
            child.put("addresses", list("""
                    SELECT sa.label, a.street, a.number, a.neighborhood, a.city, a.state, a.zip_code
                    FROM student_addresses sa JOIN addresses a ON a.id = sa.address_id WHERE sa.student_id = ?""", child.get("id")));
            child.put("absences", list("SELECT date, created_at FROM student_absences WHERE student_id = ? ORDER BY date", child.get("id")));
        }
        data.put("children", children);
        data.put("ratingsGiven", list("SELECT driver_id, score, comment, date FROM ratings WHERE responsible_id = ? ORDER BY date", userId));

        // motorista: codigo de conversa, Pix, escolas e vans
        data.put("driver", one("SELECT link_code, pix_key_type, pix_key FROM drivers WHERE user_id = ?", userId));
        data.put("schools", list("""
                SELECT s.name, ds.created_at FROM driver_schools ds JOIN schools s ON s.id = ds.school_id WHERE ds.driver_id = ?""", userId));
        data.put("vehicles", list("""
                SELECT v.plate, v.model, v.color, v.capacity, dv.start_date, dv.end_date
                FROM driver_vehicles dv JOIN vehicles v ON v.id = dv.vehicle_id WHERE dv.driver_id = ? ORDER BY dv.start_date""", userId));

        // os dois: contratos (com as parcelas), mensagens que mandou e notificacoes
        List<Map<String, Object>> contracts = list("""
                SELECT c.id, c.status, c.periodicity, c.value, c.start_date, c.end_date, c.created_at,
                       s.name AS student_name, d.name AS driver_name, r.name AS responsible_name
                FROM user_driver_contracts u JOIN contracts c ON c.id = u.contract_id JOIN students s ON s.id = u.student_id
                JOIN users d ON d.id = u.driver_id JOIN users r ON r.id = u.responsible_id
                WHERE u.driver_id = ? OR u.responsible_id = ? ORDER BY c.created_at""", userId, userId);
        for (Map<String, Object> contract : contracts) {
            contract.put("payments", list("""
                    SELECT value, due_date, status, payment_method, paid_at FROM payments WHERE contract_id = ? ORDER BY due_date""",
                    contract.get("id")));
        }
        data.put("contracts", contracts);
        data.put("messagesSent", list("""
                SELECT chat_id, content, attachment_type, sent_at FROM chat_messages WHERE sender_user_id = ? ORDER BY sent_at""", userId));
        data.put("notifications", list("SELECT type, title, message, created_at, read_at FROM notifications WHERE user_id = ? ORDER BY created_at", userId));
        return data;
    }

    private List<Map<String, Object>> list(String sql, Object... args) {
        return jdbc.queryForList(sql, args);
    }

    private Map<String, Object> one(String sql, Object... args) {
        List<Map<String, Object>> rows = jdbc.queryForList(sql, args);
        return rows.isEmpty() ? null : rows.get(0);
    }
}
