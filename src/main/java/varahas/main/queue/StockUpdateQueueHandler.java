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
	private final Map<Long, BlockingQueue<StockEvent>> queues = new ConcurrentHashMap<>();
	private final Set<Long> processing = ConcurrentHashMap.newKeySet();
	private final ExecutorService executor = Executors.newCachedThreadPool();

	@Autowired
	private VariationService variationService;

	public void enqueueEvent(Long variationId, SourceChannel channel) {
		queues.computeIfAbsent(variationId, id -> new LinkedBlockingQueue<>())
				.add(new StockUpdateEvent(variationId, channel));
		
		startProcessingIfNeeded(variationId);
	}

	public void enqueueEvent(Long variationId, Integer stock, SourceChannel channel) {
		queues.computeIfAbsent(variationId, id -> new LinkedBlockingQueue<>())
				.add(new LocalStockUpdateEvent(variationId, channel,stock));

		startProcessingIfNeeded(variationId);
	}

	private void startProcessingIfNeeded(Long variationId){
		
		if (processing.add(variationId)) {
			executor.submit(() -> processQueue(variationId));
		}
	}
	
	private void processQueue(Long variationId) {
		try {
			BlockingQueue<StockEvent> queue = queues.get(variationId);
			while (queue != null && !queue.isEmpty()) {
				StockEvent event = queue.poll(2, TimeUnit.SECONDS);
				if (event != null) {
					try {
						variationService.processStockDelta(event);
					} catch (Exception e) {
						System.err.println("Error procesando evento de stock: " + e.getMessage());
					}
				}
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} finally {
			processing.remove(variationId);
			if(queues.containsKey(variationId) && !queues.get(variationId).isEmpty()){
				startProcessingIfNeeded(variationId);
			}
		}
	}
	public interface StockEvent{
		Long variationId();
		SourceChannel source();
	}
	
	public record StockUpdateEvent(Long variationId, SourceChannel source) implements StockEvent {
	}

	public record LocalStockUpdateEvent(Long variationId, SourceChannel source,Integer stock) implements StockEvent {
	}
}
