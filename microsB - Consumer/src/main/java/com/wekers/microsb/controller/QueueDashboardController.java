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

    // HTML PAGE
    @GetMapping
    public String dashboard(Model model) {
        populateDashboardModel(model);
        return "queues";
    }

    // STATUS ONLY
    @GetMapping(value = "/api/status", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<QueueStatusResponse> getQueueStatus() {
        var body = new QueueStatusResponse(
                countMain(),
                countRetry(),
                countDead(),
                countDeleted()
        );
        return noCache(body);
    }

    // MESSAGES PER QUEUE
    @GetMapping(value = "/api/messages/{kind}", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<QueueMessagesResponse> getQueueMessages(@PathVariable String kind) {
        var q = resolveQueue(kind);
        List<String> raw = queueService.peek(q, 10);
        List<String> cleaned = raw.stream().map(this::normalizeJsonOrKeep).toList();
        var body = new QueueMessagesResponse(q, cleaned);
        return noCache(body);
    }

    // ALL MESSAGES (MAIN + RETRY + DEAD + DELETED)
    @GetMapping(value = "/api/all-messages", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<AllQueuesResponse> getAllMessages() {

        List<String> main    = queueService.peek(properties.getQueues().getMain(), 10)     .stream().map(this::normalizeJsonOrKeep).toList();
        List<String> retry   = queueService.peek(properties.getQueues().getRetry5s(), 10)  .stream().map(this::normalizeJsonOrKeep).toList();
        List<String> dead    = queueService.peek(properties.getQueues().getDead(), 10)     .stream().map(this::normalizeJsonOrKeep).toList();
        List<String> deleted = queueService.peek(properties.getQueues().getDeleted(), 10)  .stream().map(this::normalizeJsonOrKeep).toList();

        var body = new AllQueuesResponse(
                main, retry, dead, deleted,
                countMain(), countRetry(), countDead(), countDeleted()
        );

        return noCache(body);
    }

    // REPROCESS
    @PostMapping("/reprocess")
    public String reprocess(@RequestParam("body") String messageBody, RedirectAttributes ra) {
        try {
            String json = normalizeJsonOrKeep(messageBody);

            // valida JSON
            mapper.readTree(json);

            // envia como String pura
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


    // DELETE FIRST MESSAGE FROM DEAD
    @PostMapping("/delete")
    public String deleteFromDeadQueue(RedirectAttributes ra) {
        boolean deleted = queueService.deleteFirstMessage(properties.getQueues().getDead());
        ra.addFlashAttribute(deleted ? "success" : "error",
                deleted ? "Message deleted successfully"
                        : "No message to delete");
        return "redirect:/queues";
    }

    // RESOLVE QUEUE NAME
    private String resolveQueue(String kind) {
        return switch (kind) {
            case "main"    -> properties.getQueues().getMain();
            case "retry"   -> properties.getQueues().getRetry5s();
            case "dead"    -> properties.getQueues().getDead();
            case "deleted" -> properties.getQueues().getDeleted();
            default -> throw new IllegalArgumentException("Unknown queue kind: " + kind);
        };
    }

    // POPULATE FIRST SCREEN
    private void populateDashboardModel(Model model) {
        model.addAttribute("mainQueue", properties.getQueues().getMain());
        model.addAttribute("retryQueue", properties.getQueues().getRetry5s());
        model.addAttribute("deadQueue", properties.getQueues().getDead());
        model.addAttribute("deletedQueue", properties.getQueues().getDeleted());

        model.addAttribute("mainCount", countMain());
        model.addAttribute("retryCount", countRetry());
        model.addAttribute("deadCount", countDead());
        model.addAttribute("deletedCount", countDeleted());

        model.addAttribute("mainMessages",    queueService.peek(properties.getQueues().getMain(), 10).stream().map(this::normalizeJsonOrKeep).toList());
        model.addAttribute("retryMessages",   queueService.peek(properties.getQueues().getRetry5s(), 10).stream().map(this::normalizeJsonOrKeep).toList());
        model.addAttribute("deadMessages",    queueService.peek(properties.getQueues().getDead(), 10).stream().map(this::normalizeJsonOrKeep).toList());
        model.addAttribute("deletedMessages", queueService.peek(properties.getQueues().getDeleted(), 10).stream().map(this::normalizeJsonOrKeep).toList());
    }

    private long countMain()    { return queueService.getMessageCount(properties.getQueues().getMain()); }
    private long countRetry()   { return queueService.getMessageCount(properties.getQueues().getRetry5s()); }
    private long countDead()    { return queueService.getMessageCount(properties.getQueues().getDead()); }
    private long countDeleted() { return queueService.getMessageCount(properties.getQueues().getDeleted()); }

    private String normalizeJsonOrKeep(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        s = s.replaceAll("^\"+", "").replaceAll("\"+$", "");
        s = s.replace("\\r", "").replace("\\n", "")
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

    // DTOs
    public record QueueStatusResponse(long mainCount, long retryCount, long deadCount, long deletedCount) {}
    public record QueueMessagesResponse(String queueName, List<String> messages) {}
    public record AllQueuesResponse(
            List<String> mainMessages,
            List<String> retryMessages,
            List<String> deadMessages,
            List<String> deletedMessages,
            long mainCount,
            long retryCount,
            long deadCount,
            long deletedCount
    ) {}
}
