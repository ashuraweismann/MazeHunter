package com.mazehunter.server.game;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Dedicated single-threaded authoritative game loop running at a fixed tick rate (20 TPS).
 * Drains commands from the concurrent queue and executes simulation updates.
 */
public class GameLoop implements Runnable {

    private static final Logger LOGGER = LoggerFactory.getLogger(GameLoop.class);
    public static final int TICK_RATE = 20; // 20 ticks per second
    public static final long TICK_DURATION_MS = 1000 / TICK_RATE; // 50 ms
    public static final int MAX_QUEUE_CAPACITY = 1000;

    private final MatchManager matchManager;
    private final Queue<ClientCommand> commandQueue = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean running = new AtomicBoolean(false);

    private Thread loopThread;
    private long tickIndex = 0;

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

    public void enqueueCommand(ClientCommand command) {
        if (command != null && running.get()) {
            if (commandQueue.size() < MAX_QUEUE_CAPACITY) {
                commandQueue.offer(command);
            } else {
                LOGGER.warn("Command queue full ({} items). Dropping command from player {}",
                        commandQueue.size(), command.playerId());
            }
        }
    }

    public int getQueueSize() {
        return commandQueue.size();
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
}
