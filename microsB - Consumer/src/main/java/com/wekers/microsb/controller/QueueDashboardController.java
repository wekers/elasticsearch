package com.wekers.microsb.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wekers.microsb.config.RabbitMQProperties;
import com.wekers.microsb.service.RabbitQueueService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Slf4j
@Controller
@RequestMapping("/queues")
@RequiredArgsConstructor
public class QueueDashboardController {

    private final RabbitQueueService queueService;
    private final RabbitTemplate rabbitTemplate;
    private final RabbitMQProperties properties;
    private final ObjectMapper mapper = new ObjectMapper();

    // Página HTML
    @GetMapping
    public String dashboard(Model model) {
        populateDashboardModel(model);
        return "queues";
    }

    // ---- REST: status agregado (contadores)
    @GetMapping(value = "/api/status", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<QueueStatusResponse> getQueueStatus() {
        var body = new QueueStatusResponse(
                countMain(), countRetry(), countDead()
        );
        return noCache(body);
    }

    // ---- REST: mensagens por fila (com validação)
    @GetMapping(value = "/api/messages/{kind}", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<QueueMessagesResponse> getQueueMessages(@PathVariable String kind) {
        var q = resolveQueue(kind);
        List<String> raw = queueService.peek(q, 10);
        List<String> cleaned = raw.stream().map(this::normalizeJsonOrKeep).toList();
        var body = new QueueMessagesResponse(q, cleaned);
        return noCache(body);
    }

    // ---- REST: tudo (contadores + mensagens)
    @GetMapping(value = "/api/all-messages", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<AllQueuesResponse> getAllMessages() {
        List<String> main = queueService.peek(properties.getQueues().getMain(), 10).stream().map(this::normalizeJsonOrKeep).toList();
        List<String> retry = queueService.peek(properties.getQueues().getRetry5s(), 10).stream().map(this::normalizeJsonOrKeep).toList();
        List<String> dead = queueService.peek(properties.getQueues().getDead(), 10).stream().map(this::normalizeJsonOrKeep).toList();

        var body = new AllQueuesResponse(
                main, retry, dead,
                countMain(), countRetry(), countDead()
        );
        return noCache(body);
    }

    // Reprocessar (recebe JSON e republica como objeto)
    @PostMapping("/reprocess")
    public String reprocess(@RequestParam("body") String messageBody, RedirectAttributes ra) {
        try {
            String json = normalizeJsonOrKeep(messageBody);
            JsonNode node = mapper.readTree(json); // valida JSON
            rabbitTemplate.convertAndSend(
                    properties.getExchanges().getMain(),
                    properties.getRoutingKeys().getCreated(),
                    mapper.convertValue(node, Map.class) // ou ProductDocument.class se quiser tipar
            );
            ra.addFlashAttribute("success", "Message reprocessed successfully");
        } catch (Exception e) {
            log.error("Error reprocessing message", e);
            ra.addFlashAttribute("error", "Failed to reprocess message: " + e.getMessage());
        }
        return "redirect:/queues";
    }

    @PostMapping("/delete")
    public String deleteFromDeadQueue(RedirectAttributes ra) {
        boolean deleted = queueService.deleteFirstMessage(properties.getQueues().getDead());
        ra.addFlashAttribute(deleted ? "success" : "error",
                deleted ? "Message deleted successfully" : "No message to delete");
        return "redirect:/queues";
    }

    // ----------------- Helpers -----------------

    private String resolveQueue(String kind) {
        // aceita aliases amigáveis
        return switch (kind) {
            case "main"  -> properties.getQueues().getMain();
            case "retry" -> properties.getQueues().getRetry5s();
            case "dead"  -> properties.getQueues().getDead();
            default -> throw new IllegalArgumentException("Unknown queue kind: " + kind);
        };
    }

    private void populateDashboardModel(Model model) {
        model.addAttribute("mainQueue", properties.getQueues().getMain());
        model.addAttribute("retryQueue", properties.getQueues().getRetry5s());
        model.addAttribute("deadQueue", properties.getQueues().getDead());
        model.addAttribute("mainCount", countMain());
        model.addAttribute("retryCount", countRetry());
        model.addAttribute("deadCount", countDead());
        // Primeiro paint já com dados
        model.addAttribute("mainMessages", queueService.peek(properties.getQueues().getMain(), 10).stream().map(this::normalizeJsonOrKeep).toList());
        model.addAttribute("retryMessages", queueService.peek(properties.getQueues().getRetry5s(), 10).stream().map(this::normalizeJsonOrKeep).toList());
        model.addAttribute("deadMessages", queueService.peek(properties.getQueues().getDead(), 10).stream().map(this::normalizeJsonOrKeep).toList());
    }

    // Tenta ler como JSON; se falhar, limpa escapes comuns; se ainda assim não der, devolve cru.
    private String normalizeJsonOrKeep(String raw) {
        if (raw == null) return "";
        String s = raw.trim();

        // 1) tira aspas externas repetidas
        s = s.replaceAll("^\"+", "").replaceAll("\"+$", "");

        // 2) tenta desescapar \r \n \"
        s = s
                .replace("\\r", "")
                .replace("\\n", "")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");

        // 3) tenta parsear como JSON; se conseguir, reserializa bonito
        try {
            var json = mapper.readTree(s.getBytes(StandardCharsets.UTF_8));
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(json);
        } catch (Exception ignored) {
            // não era JSON válido; retorna texto “limpo”
            return s;
        }
    }

    private long countMain() { return queueService.getMessageCount(properties.getQueues().getMain()); }
    private long countRetry() { return queueService.getMessageCount(properties.getQueues().getRetry5s()); }
    private long countDead()  { return queueService.getMessageCount(properties.getQueues().getDead()); }

    private <T> ResponseEntity<T> noCache(T body) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ZERO).noCache().mustRevalidate().cachePrivate())
                .header("Pragma", "no-cache")
                .body(body);
    }

    // DTOs
    public record QueueStatusResponse(long mainCount, long retryCount, long deadCount) {}
    public record QueueMessagesResponse(String queueName, List<String> messages) {}
    public record AllQueuesResponse(
            List<String> mainMessages, List<String> retryMessages, List<String> deadMessages,
            long mainCount, long retryCount, long deadCount) {}
}
