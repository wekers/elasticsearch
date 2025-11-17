package com.wekers.microsb.controller;

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

@Slf4j
@Controller
@RequestMapping("/queues")
@RequiredArgsConstructor
public class QueueDashboardController {

    private final RabbitQueueService queueService;
    private final RabbitMQProperties properties;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper mapper = new ObjectMapper();

    // ================================
    // HTML PAGE
    // ================================
    @GetMapping
    public String dashboard(Model model) {
        populateDashboardModel(model);
        return "queues";
    }

    // ================================
    // STATUS API
    // ================================
    @GetMapping(value = "/api/status", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<QueueStatusResponse> getQueueStatus() {
        var body = new QueueStatusResponse(
                countCreated(),
                countUpdated(),
                countDeleted(),
                countRetry(),
                countDead()
        );
        return noCache(body);
    }

    // ================================
    // PER QUEUE MESSAGES
    // ================================
    @GetMapping(value = "/api/messages/{kind}", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<QueueMessagesResponse> getQueueMessages(@PathVariable String kind) {
        String queue = resolveQueue(kind);
        List<String> raw = queueService.peek(queue, 10);
        List<String> cleaned = raw.stream().map(this::normalizeJsonOrKeep).toList();
        return noCache(new QueueMessagesResponse(queue, cleaned));
    }

    // ================================
    // ALL QUEUES MESSAGES
    // ================================
    @GetMapping(value = "/api/all-messages", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<AllQueuesResponse> getAllMessages() {

        var c = properties.getQueues();

        var body = new AllQueuesResponse(
                queueService.peek(c.getCreated(), 10).stream().map(this::normalizeJsonOrKeep).toList(),
                queueService.peek(c.getUpdated(), 10).stream().map(this::normalizeJsonOrKeep).toList(),
                queueService.peek(c.getDeleted(), 10).stream().map(this::normalizeJsonOrKeep).toList(),
                queueService.peek(c.getRetry5s(), 10).stream().map(this::normalizeJsonOrKeep).toList(),
                queueService.peek(c.getDead(), 10).stream().map(this::normalizeJsonOrKeep).toList(),
                countCreated(), countUpdated(), countDeleted(),
                countRetry(), countDead()
        );

        return noCache(body);
    }

    // ================================
    // REPROCESS MESSAGE (DLQ or others)
    // ================================
    @PostMapping("/reprocess")
    public String reprocess(@RequestParam("body") String messageBody, RedirectAttributes ra) {
        try {
            String json = normalizeJsonOrKeep(messageBody);

            // valida JSON
            mapper.readTree(json);

            // envia para products.created
            rabbitTemplate.convertAndSend(
                    properties.getExchanges().getMain(),
                    properties.getRoutingKeys().getCreated(),
                    json
            );

            ra.addFlashAttribute("success", "Message reprocessed successfully");
        } catch (Exception e) {
            log.error("Error reprocessing message", e);
            ra.addFlashAttribute("error", "Failed to reprocess message: " + e.getMessage());
        }
        return "redirect:/queues";
    }


    // ================================
    // DELETE FIRST MESSAGE FROM DLQ
    // ================================
    @PostMapping("/delete")
    public String deleteFromDeadQueue(RedirectAttributes ra) {
        boolean deleted = queueService.deleteFirstMessage(properties.getQueues().getDead());
        ra.addFlashAttribute(deleted ? "success" : "error",
                deleted ? "Deleted" : "Nothing to delete");
        return "redirect:/queues";
    }

    // ================================
    // HELPERS
    // ================================
    private String resolveQueue(String kind) {
        var q = properties.getQueues();
        return switch (kind) {
            case "created" -> q.getCreated();
            case "updated" -> q.getUpdated();
            case "deleted" -> q.getDeleted();
            case "retry"   -> q.getRetry5s();
            case "dead"    -> q.getDead();
            default -> throw new IllegalArgumentException("Unknown queue: " + kind);
        };
    }

    private void populateDashboardModel(Model m) {
        var q = properties.getQueues();
        m.addAttribute("createdQueue", q.getCreated());
        m.addAttribute("updatedQueue", q.getUpdated());
        m.addAttribute("deletedQueue", q.getDeleted());
        m.addAttribute("retryQueue", q.getRetry5s());
        m.addAttribute("deadQueue", q.getDead());
    }

    private long countCreated() { return queueService.getMessageCount(properties.getQueues().getCreated()); }
    private long countUpdated() { return queueService.getMessageCount(properties.getQueues().getUpdated()); }
    private long countDeleted() { return queueService.getMessageCount(properties.getQueues().getDeleted()); }
    private long countRetry()   { return queueService.getMessageCount(properties.getQueues().getRetry5s()); }
    private long countDead()    { return queueService.getMessageCount(properties.getQueues().getDead()); }

    private String normalizeJsonOrKeep(String raw) {
        if (raw == null) return "";
        String s = raw.trim()
                .replaceAll("^\"+", "")
                .replaceAll("\"+$", "")
                .replace("\\r", "")
                .replace("\\n", "")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");

        try {
            var json = mapper.readTree(s.getBytes(StandardCharsets.UTF_8));
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(json);
        } catch (Exception ignored) {
            return s;
        }
    }

    private <T> ResponseEntity<T> noCache(T body) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ZERO).noCache().mustRevalidate().cachePrivate())
                .header("Pragma", "no-cache")
                .body(body);
    }

    public record QueueStatusResponse(long created, long updated, long deleted, long retry, long dead) {}
    public record QueueMessagesResponse(String queueName, List<String> messages) {}
    public record AllQueuesResponse(
            List<String> createdMessages,
            List<String> updatedMessages,
            List<String> deletedMessages,
            List<String> retryMessages,
            List<String> deadMessages,
            long createdCount,
            long updatedCount,
            long deletedCount,
            long retryCount,
            long deadCount
    ) {}
}
