package varahas.main.notifications;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import varahas.main.dto.StockUpdate;

@Component
public class StockNotificationService {

	
    @Autowired
    private SimpMessagingTemplate messagingTemplate;
    
    public void sendUpdate(Long itemId, int newStock) {
        StockUpdate update = new StockUpdate(itemId, newStock);
        messagingTemplate.convertAndSend("/topic/stock", update);
    }
}
