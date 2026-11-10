package com.mazehunter.server.game;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Dedicated single-threaded authoritative game loop running at a fixed tick rate (20 TPS).
 * Drains commands from the concurrent queue and executes simulation updates.
 */
public class GameLoop implements Runnable {

    private static final Logger LOGGER = LoggerFactory.getLogger(GameLoop.class);
    public static final int TICK_RATE = 20; // 20 ticks per second
    public static final long TICK_DURATION_MS = 1000 / TICK_RATE; // 50 ms
    public static final int MAX_QUEUE_CAPACITY = 1000;
    public static final int MAX_COMMANDS_PER_SECOND = 30;
    private static final long NANOS_PER_SECOND = 1_000_000_000L;

    private final MatchManager matchManager;
    private final Queue<ClientCommand> commandQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentHashMap<String, CommandRateLimiter> commandRateLimiters = new ConcurrentHashMap<>();
    private final AtomicLong droppedCommandCount = new AtomicLong();
    private final AtomicBoolean running = new AtomicBoolean(false);

    private Thread loopThread;
    private volatile long tickIndex;

    public GameLoop(MatchManager matchManager) {
        this.matchManager = Objects.requireNonNull(matchManager, "matchManager");
    }

    public synchronized void start() {
        if (running.compareAndSet(false, true)) {
            loopThread = new Thread(this, "authoritative-game-loop");
            loopThread.setDaemon(true);
            loopThread.start();
            LOGGER.info("GameLoop thread started at {} TPS ({} ms/tick)", TICK_RATE, TICK_DURATION_MS);
        }
    }

    public synchronized void stop() {
        if (running.compareAndSet(true, false)) {
            LOGGER.info("Stopping GameLoop thread...");
            if (loopThread != null) {
                loopThread.interrupt();
                try {
                    loopThread.join(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    public boolean isRunning() {
        return running.get();
    }

    public boolean enqueueCommand(ClientCommand command) {
        if (command == null || !running.get()) {
            return false;
        }

        AtomicBoolean enqueued = new AtomicBoolean();
        long now = System.nanoTime();
        commandRateLimiters.compute(command.playerId(), (playerId, limiter) -> {
            CommandRateLimiter current = limiter == null ? new CommandRateLimiter(now) : limiter;
            if (commandQueue.size() >= MAX_QUEUE_CAPACITY) {
                droppedCommandCount.incrementAndGet();
                LOGGER.warn("Command queue full ({} items). Dropping command from player {}",
                        commandQueue.size(), command.playerId());
                return current;
            }
            if (!current.tryAcquire(now)) {
                droppedCommandCount.incrementAndGet();
                return current;
            }
            enqueued.set(commandQueue.offer(command));
            if (!enqueued.get()) {
                droppedCommandCount.incrementAndGet();
            }
            return current;
        });
        return enqueued.get();
    }

    public void removePlayerCommands(String playerId) {
        if (playerId == null) {
            return;
        }
        commandRateLimiters.remove(playerId);
        commandQueue.removeIf(command -> playerId.equals(command.playerId()));
    }

    public int getQueueSize() {
        return commandQueue.size();
    }

    public long getDroppedCommandCount() {
        return droppedCommandCount.get();
    }

    public long getTickIndex() {
        return tickIndex;
    }

    @Override
    public void run() {
        long nextTickTime = System.currentTimeMillis();

        while (running.get()) {
            try {
                // 1. Drain pending commands from concurrent network readers
                ClientCommand command;
                while ((command = commandQueue.poll()) != null) {
                    matchManager.processCommand(command);
                }

                // 2. Execute authoritative simulation tick
                matchManager.tick(tickIndex++);

                // 3. Precise tick sleep
                nextTickTime += TICK_DURATION_MS;
                long sleepMs = nextTickTime - System.currentTimeMillis();
                if (sleepMs > 0) {
                    Thread.sleep(sleepMs);
                } else if (sleepMs < -500) {
                    // Severely lagging: reset tick target to current time
                    LOGGER.warn("GameLoop lagging behind by {} ms. Catching up.", -sleepMs);
                    nextTickTime = System.currentTimeMillis();
                }
            } catch (InterruptedException e) {
                LOGGER.info("GameLoop thread interrupted, terminating.");
                break;
            } catch (Exception e) {
                LOGGER.error("Unexpected exception in GameLoop tick: {}", e.getMessage(), e);
            }
        }

        LOGGER.info("GameLoop thread exited.");
    }

    private static final class CommandRateLimiter {
        private double availableTokens = MAX_COMMANDS_PER_SECOND;
        private long lastRefillNanos;

        private CommandRateLimiter(long now) {
            lastRefillNanos = now;
        }

        private boolean tryAcquire(long now) {
            long elapsedNanos = Math.max(0, now - lastRefillNanos);
            availableTokens = Math.min(
                    MAX_COMMANDS_PER_SECOND,
                    availableTokens + elapsedNanos * (double) MAX_COMMANDS_PER_SECOND / NANOS_PER_SECOND);
            lastRefillNanos = now;
            if (availableTokens < 1) {
                return false;
            }
            availableTokens -= 1;
            return true;
        }
    }
}
