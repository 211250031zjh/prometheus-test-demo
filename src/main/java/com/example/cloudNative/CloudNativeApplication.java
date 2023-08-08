package com.example.cloudNative;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@SpringBootApplication
public class CloudNativeApplication {

	public static void main(String[] args) {
		SpringApplication.run(CloudNativeApplication.class, args);
	}

	@RestController
	public class HelloWorldController {
		private final RateLimiter rateLimiter = new RateLimiter(100, TimeUnit.SECONDS);

		@GetMapping("/hello")
		public ResponseEntity<String> hello() {
			if (rateLimiter.tryAcquire()) {
				return ResponseEntity.ok("{\"msg\":\"hello\"}");
			} else {
				return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body("{\"msg\":\"Too many requests\"}");
			}
		}

		public void shutdown() {
			rateLimiter.stop();
		}
	}

	public class TooManyRequestsException extends RuntimeException {
		private static final int STATUS_CODE = 429;
		private static final String MESSAGE = "Too many requests";

		public TooManyRequestsException() {
			super(MESSAGE);
		}

		public int getStatusCode() {
			return STATUS_CODE;
		}

		public String getMessage() {
			return MESSAGE;
		}
	}

	class RateLimiter {
		private final ThreadPoolExecutor executor;
		private final BlockingQueue<Runnable> queue = new LinkedBlockingQueue<>();
		private final int rate;
		private final TimeUnit unit;
		private int tokens;
		private boolean running;

		public RateLimiter(int rate, TimeUnit unit) {
			this.rate = rate;
			this.unit = unit;
			this.tokens = rate;
			this.executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, queue);
			this.running = false;
		}

		public synchronized boolean tryAcquire() {
			if (tokens > 0) {
				tokens--;
				return true;
			} else {
				return false;
			}
		}

		public synchronized void start() {
			if (!running) {
				running = true;
				Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(() -> {
					synchronized (this) {
						tokens = rate;
					}
				}, 0, 1, unit);
			}
		}

		public synchronized void stop() {
			if (running) {
				running = false;
				executor.shutdown();
			}
		}
	}
}

