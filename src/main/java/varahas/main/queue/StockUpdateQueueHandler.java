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
		BlockingQueue<StockEvent> q = queues.get(variationId);
		System.out.println("⬆️ Encolado evento para variación " + variationId + ", canal=" + channel + ", tamaño cola=" + (q != null ? q.size() : 0));
		startProcessingIfNeeded(variationId);
	}

	public void enqueueEvent(Long variationId, Integer stock, SourceChannel channel) {
		queues.computeIfAbsent(variationId, id -> new LinkedBlockingQueue<>())
				.add(new LocalStockUpdateEvent(variationId, channel, stock));
		BlockingQueue<StockEvent> q = queues.get(variationId);
		System.out.println("⬆️ Encolado evento LOCAL para variación " + variationId + ", stock=" + stock + ", canal=" + channel + ", tamaño cola=" + (q != null ? q.size() : 0));
		startProcessingIfNeeded(variationId);
	}

	private void startProcessingIfNeeded(Long variationId){
		boolean willStart = processing.add(variationId);
		System.out.println("🧵 Verificando procesamiento para variación " + variationId + ": iniciar=" + willStart);
		if (willStart) {
			executor.submit(() -> processQueue(variationId));
			System.out.println("🚀 Procesamiento iniciado para variación " + variationId);
		}
	}
	
	private void processQueue(Long variationId) {
		System.out.println("▶️ Iniciando loop de procesamiento para variación " + variationId);
		try {
			BlockingQueue<StockEvent> queue = queues.get(variationId);
			while (queue != null && !queue.isEmpty()) {
				StockEvent event = queue.poll(2, TimeUnit.SECONDS);
				if (event != null) {
					try {
						System.out.println("🔄 Procesando evento para variación " + variationId + ", tipo=" + event.getClass().getSimpleName() + ", canal=" + event.source());
						variationService.processStockDelta(event);
						System.out.println("✅ Evento procesado para variación " + variationId);
					} catch (Exception e) {
						System.err.println("Error procesando evento de stock para variación " + variationId + ": " + e.getMessage());
					}
				}
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			System.err.println("Procesamiento interrumpido para variación " + variationId + ": " + e.getMessage());
		} finally {
			processing.remove(variationId);
			boolean hasMore = queues.containsKey(variationId) && !queues.get(variationId).isEmpty();
			System.out.println("⏹️ Finalizado procesamiento para variación " + variationId + ", quedan_pendientes=" + hasMore);
			if(hasMore){
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
