package varahas.main.queue;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import varahas.main.enums.SourceChannel;
import varahas.main.services.VariationService;

@Component
public class StockUpdateQueueHandler {
	private final Map<Long, BlockingQueue<StockUpdateEvent>> queues = new ConcurrentHashMap<>();
    private final Set<Long> processing = ConcurrentHashMap.newKeySet();
    private final ExecutorService executor = Executors.newCachedThreadPool();

    @Autowired
    private VariationService variationService;

    public void enqueueEvent(Long variationId, int newRemoteStock, SourceChannel channel) {
        queues.computeIfAbsent(variationId, id -> new LinkedBlockingQueue<>())
              .add(new StockUpdateEvent(variationId, newRemoteStock, channel));

        if (processing.add(variationId)) {
            executor.submit(() -> processQueue(variationId));
        }
    }

    private void processQueue(Long variationId) {
        try {
            BlockingQueue<StockUpdateEvent> queue = queues.get(variationId);
            while (queue != null && !queue.isEmpty()) {
                StockUpdateEvent event = queue.poll(1, TimeUnit.SECONDS);
                if (event != null) {
                    try {
                        variationService.processStockDelta(event);
                    } catch (Exception e) {
                        System.err.println("Error procesando evento de stock: " + e.getMessage());
                    }
                }
            }
        } catch (InterruptedException ignored) {
        } finally {
            processing.remove(variationId);
        }
    }

    public record StockUpdateEvent(Long variationId, int stock, SourceChannel source) {}

}
