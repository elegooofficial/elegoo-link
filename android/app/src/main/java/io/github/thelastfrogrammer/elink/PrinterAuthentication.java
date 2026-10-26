package io.github.thelastfrogrammer.elink;

/** Explicit credential intent. Never infer a pairing PIN from an entered LAN code. */
public final class PrinterAuthentication {
    public final boolean pinProbe;
    private final String pin;
    public PrinterAuthentication(boolean pinProbe, String pin) {
        this.pinProbe = pinProbe; this.pin = pinProbe ? pin : "";
        if (pinProbe) {
            if (pin == null || pin.trim().isEmpty() || pin.length() > 256) throw new IllegalArgumentException("Enter the current printer-displayed pairing PIN for this probe.");
            for (int i = 0; i < pin.length(); i++) if (Character.isISOControl(pin.charAt(i))) throw new IllegalArgumentException("Pairing PIN contains invalid characters.");
        }
    }
    public String password(Cc2Discovery.Info info, String lanToken) throws Exception {
        if (pinProbe) {
            if (info != null && Boolean.TRUE.equals(info.lanOnly)) throw new PrinterErrors.ModeMismatch();
            return pin; // No default PIN, code-protection override, or credential fallback.
        }
        if (info != null && Boolean.FALSE.equals(info.lanOnly)) throw new PrinterErrors.CloudMode();
        return info != null && Boolean.FALSE.equals(info.codeProtected) ? "123456" : lanToken;
    }
}
