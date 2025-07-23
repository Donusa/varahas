package varahas.main.notifications;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import varahas.main.dto.StockUpdateDto;

@Component
public class StockNotificationService {

	
    @Autowired
    private SimpMessagingTemplate messagingTemplate;
    
    public void sendUpdate(StockUpdateDto update) {
        messagingTemplate.convertAndSend("/topic/stock", update);
    }
}
