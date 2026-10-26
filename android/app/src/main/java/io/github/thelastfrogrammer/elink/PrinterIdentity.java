package io.github.thelastfrogrammer.elink;

/** Identity lookup is independent of MQTT authentication. No HTTP dependency when discovery succeeds. */
public final class PrinterIdentity implements Cc2Session.IdentityResolver {
    private final String manual;
    private final Cc2Discovery discovery;
    private final PrinterAuthentication auth;
    private Cc2Discovery.Info info;
    private boolean mismatch;
    public PrinterIdentity(String manual, Cc2Discovery discovery) {
        this(manual, discovery, new PrinterAuthentication(false, ""));
    }
    public PrinterIdentity(String manual, Cc2Discovery discovery, PrinterAuthentication auth) {
        this.manual = manual == null ? "" : manual.trim(); this.discovery = discovery;
        this.auth = auth;
        if (!this.manual.isEmpty() && !Cc2Discovery.validSerial(this.manual)) throw new IllegalArgumentException("Invalid serial number");
    }
    public String resolve(PrinterHttp http) throws Exception {
        try {
            info = discovery.discoverInfo(http.host());
            mismatch = !manual.isEmpty() && !manual.equals(info.serial);
            return info.serial;
        }
        catch (java.io.IOException discoveryError) {
            if (discoveryError instanceof VpnRouteGuard.Unavailable) throw discoveryError;
            if (!manual.isEmpty()) return manual;
            if (auth.pinProbe) throw new PrinterErrors.IdentityUnavailable(); // Never bootstrap with a PIN in HTTP.
            try { return http.systemInfo().getString("sn"); }
            catch (Exception error) {
                if (error instanceof VpnRouteGuard.Unavailable || error instanceof PrinterErrors.Rejected || (error instanceof PrinterErrors.HttpStatus
                    && (((PrinterErrors.HttpStatus) error).status == 401 || ((PrinterErrors.HttpStatus) error).status == 403))) throw error;
                throw new PrinterErrors.IdentityUnavailable();
            }
        }
    }
    public String password(PrinterHttp http) throws Exception {
        return auth.password(info, http.token());
    }
    public String summary() {
        String mode = auth.pinProbe ? "Cloud-mode local PIN probe: read-only, no automatic retries. Matrix coexistence is unverified. " : "";
        if (info == null) return mode + "Printer mode and code-protection state were not available from UDP discovery.";
        return mode + info.summary() + (mismatch ? " Manual serial differed; using the discovered printer serial." : "");
    }
    @Override public boolean readOnly() { return auth.pinProbe; }
    @Override public void close() { discovery.close(); }
}
