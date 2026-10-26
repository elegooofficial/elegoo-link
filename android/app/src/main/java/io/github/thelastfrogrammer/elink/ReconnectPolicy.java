package io.github.thelastfrogrammer.elink;

/** Bounded retries apply to connection establishment only, never to printer commands. */
public final class ReconnectPolicy {
    private int attempts;
    public int nextDelaySeconds() {
        if (attempts >= 5) return -1;
        return 1 << attempts++;
    }
    public void connected() { attempts = 0; }
    public int attempts() { return attempts; }
}
