package com.wekers.microsb.controller;

import com.wekers.microsb.config.RabbitMQProperties;
import com.wekers.microsb.service.RabbitQueueService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller
@RequestMapping("/queues")
@RequiredArgsConstructor
public class QueueDashboardController {

    private final RabbitQueueService queueService;
    private final RabbitTemplate rabbitTemplate;
    private final RabbitMQProperties properties;

    @GetMapping
    public String dashboard(Model model) {
        model.addAttribute("mainQueue", properties.getQueues().getMain());
        model.addAttribute("retryQueue", properties.getQueues().getRetry5s());
        model.addAttribute("deadQueue", properties.getQueues().getDead());

        // Limitar a 10 mensagens para evitar loops
        model.addAttribute("mainMessages", queueService.peek(properties.getQueues().getMain(), 10));
        model.addAttribute("retryMessages", queueService.peek(properties.getQueues().getRetry5s(), 10));
        model.addAttribute("deadMessages", queueService.peek(properties.getQueues().getDead(), 10));

        model.addAttribute("mainCount", queueService.getMessageCount(properties.getQueues().getMain()));
        model.addAttribute("retryCount", queueService.getMessageCount(properties.getQueues().getRetry5s()));
        model.addAttribute("deadCount", queueService.getMessageCount(properties.getQueues().getDead()));

        return "queues";
    }

    @PostMapping("/reprocess")
    public String reprocess(@RequestParam("body") String messageBody, RedirectAttributes redirectAttributes) {
        try {
            // Limpeza específica para a barra inicial
            String cleanedBody = cleanInitialBackslash(messageBody);

            log.debug("Reprocessing message - Original: '{}', Cleaned: '{}'", messageBody, cleanedBody);

            rabbitTemplate.convertAndSend(properties.getExchanges().getMain(),
                    properties.getRoutingKeys().getCreated(),
                    cleanedBody);
            redirectAttributes.addFlashAttribute("success", "Message reprocessed successfully");
        } catch (Exception e) {
            log.error("Error reprocessing message", e);
            redirectAttributes.addFlashAttribute("error", "Failed to reprocess message: " + e.getMessage());
        }
        return "redirect:/queues";
    }

    private String cleanInitialBackslash(String messageBody) {
        if (messageBody == null || messageBody.isEmpty()) {
            return messageBody;
        }

        String cleaned = messageBody.trim();

        // Remove a barra inicial se existir
        if (cleaned.startsWith("\\")) {
            cleaned = cleaned.substring(1);
        }

        // Remove escapes restantes
        cleaned = cleaned.replace("\\\\", "\\")
                .replace("\\\"", "\"");

        return cleaned;
    }



    @PostMapping("/delete")
    public String deleteFromDeadQueue(RedirectAttributes redirectAttributes) {
        boolean deleted = queueService.deleteFirstMessage(properties.getQueues().getDead());
        if (deleted) {
            redirectAttributes.addFlashAttribute("success", "Message deleted successfully");
        } else {
            redirectAttributes.addFlashAttribute("error", "No message to delete");
        }
        return "redirect:/queues";
    }
}